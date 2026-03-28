from typing import Literal

from pydantic import BaseModel, Field


class TodayGuidanceDraft(BaseModel):
    summary: str = Field(min_length=1, max_length=300)
    focus: str = Field(min_length=1, max_length=220)
    disclaimer: str = Field(min_length=1, max_length=120)


class TodayGuidanceResponse(TodayGuidanceDraft):
    source: Literal["openai", "fallback"]
    model: str | None = None


class WeeklyReportDraft(BaseModel):
    headline: str = Field(min_length=1, max_length=220)
    pattern: str = Field(min_length=1, max_length=320)
    reflection: str = Field(min_length=1, max_length=320)
    next_step: str = Field(min_length=1, max_length=220)
    disclaimer: str = Field(min_length=1, max_length=120)


class WeeklyReportResponse(WeeklyReportDraft):
    source: Literal["openai", "fallback"]
    model: str | None = None


class GatewayStatusResponse(BaseModel):
    status: Literal["ok"] = "ok"
    openai_configured: bool
    fallback_only: bool
    model: str | None = None

