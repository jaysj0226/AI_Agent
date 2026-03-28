from fastapi import APIRouter, Header, HTTPException, status

from app.schemas.requests import TodayGuidanceRequest, WeeklyReportRequest
from app.schemas.responses import GatewayStatusResponse, TodayGuidanceResponse, WeeklyReportResponse
from app.services.ai_gateway_service import AIGatewayService

router = APIRouter()
service = AIGatewayService()


def verify_app_token(x_app_token: str | None = Header(default=None)) -> None:
    expected_token = service.settings.app_shared_token
    if not expected_token:
        return

    if x_app_token != expected_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid app token.",
        )


@router.get("/status", response_model=GatewayStatusResponse)
def get_status(x_app_token: str | None = Header(default=None)) -> GatewayStatusResponse:
    verify_app_token(x_app_token)
    return service.get_status()


@router.post("/ai/today-guidance", response_model=TodayGuidanceResponse)
def generate_today_guidance(
    payload: TodayGuidanceRequest,
    x_app_token: str | None = Header(default=None),
) -> TodayGuidanceResponse:
    verify_app_token(x_app_token)
    return service.generate_today_guidance(payload)


@router.post("/ai/weekly-report", response_model=WeeklyReportResponse)
def generate_weekly_report(
    payload: WeeklyReportRequest,
    x_app_token: str | None = Header(default=None),
) -> WeeklyReportResponse:
    verify_app_token(x_app_token)
    return service.generate_weekly_report(payload)
