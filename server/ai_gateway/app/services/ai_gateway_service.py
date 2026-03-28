import hashlib
import json
import logging
import time

from openai import OpenAI

from app.core.config import get_settings
from app.prompts.wellness import TODAY_GUIDANCE_INSTRUCTIONS, WEEKLY_REPORT_INSTRUCTIONS
from app.schemas.requests import TodayGuidanceRequest, WeeklyReportRequest
from app.schemas.responses import (
    GatewayStatusResponse,
    TodayGuidanceDraft,
    TodayGuidanceResponse,
    WeeklyReportDraft,
    WeeklyReportResponse,
)
from app.services.fallback_writer import build_today_fallback, build_weekly_fallback

logger = logging.getLogger(__name__)

_today_cache: dict[str, tuple[float, TodayGuidanceResponse]] = {}
_TODAY_CACHE_TTL = 600  # 10 minutes


def _today_cache_key(request: TodayGuidanceRequest) -> str:
    raw = json.dumps(request.model_dump(), sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(raw.encode()).hexdigest()


class AIGatewayService:
    def __init__(self) -> None:
        self.settings = get_settings()
        self.client = (
            OpenAI(api_key=self.settings.openai_api_key, timeout=self.settings.openai_timeout_seconds)
            if self.settings.openai_api_key
            else None
        )

    def get_status(self) -> GatewayStatusResponse:
        openai_configured = bool(self.settings.openai_api_key)
        return GatewayStatusResponse(
            openai_configured=openai_configured,
            fallback_only=not openai_configured,
            model=self.settings.openai_model if openai_configured else None,
        )

    def generate_today_guidance(self, request: TodayGuidanceRequest) -> TodayGuidanceResponse:
        if self.client is None:
            return build_today_fallback(request)

        key = _today_cache_key(request)
        cached = _today_cache.get(key)
        if cached and (time.time() - cached[0]) < _TODAY_CACHE_TTL:
            return cached[1]

        try:
            payload = json.dumps(request.model_dump(), ensure_ascii=False)
            completion = self.client.chat.completions.create(
                model=self.settings.openai_model,
                messages=[
                    {"role": "system", "content": TODAY_GUIDANCE_INSTRUCTIONS},
                    {"role": "user", "content": payload},
                ],
                response_format={"type": "json_object"},
                temperature=0.7,
                max_tokens=512,
            )
            raw = completion.choices[0].message.content
            if not raw:
                raise ValueError("Empty completion content.")

            parsed = TodayGuidanceDraft.model_validate_json(raw)
            result = TodayGuidanceResponse(
                source="openai",
                model=self.settings.openai_model,
                **parsed.model_dump(),
            )
            _today_cache[key] = (time.time(), result)
            return result
        except Exception:
            logger.exception("OpenAI today-guidance generation failed. Falling back to deterministic copy.")
            return build_today_fallback(request)

    def generate_weekly_report(self, request: WeeklyReportRequest) -> WeeklyReportResponse:
        if self.client is None:
            return build_weekly_fallback(request)

        try:
            payload = json.dumps(request.model_dump(), ensure_ascii=False, indent=2)
            response = self.client.responses.parse(
                model=self.settings.openai_model,
                reasoning={"effort": self.settings.openai_reasoning_effort},
                instructions=WEEKLY_REPORT_INSTRUCTIONS,
                input=payload,
                text_format=WeeklyReportDraft,
            )
            parsed = response.output_parsed
            if parsed is None:
                raise ValueError("Structured output was empty.")

            return WeeklyReportResponse(
                source="openai",
                model=self.settings.openai_model,
                **parsed.model_dump(),
            )
        except Exception:
            logger.exception("OpenAI weekly-report generation failed. Falling back to deterministic copy.")
            return build_weekly_fallback(request)

