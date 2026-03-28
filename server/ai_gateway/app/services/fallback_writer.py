from app.schemas.requests import TodayGuidanceRequest, WeeklyReportRequest
from app.schemas.responses import TodayGuidanceResponse, WeeklyReportResponse


def build_today_fallback(request: TodayGuidanceRequest) -> TodayGuidanceResponse:
    score = request.deterministic_score
    metrics = request.metrics

    if score >= 70:
        summary = "최근 신호가 비교적 안정적입니다. 오늘 리듬을 크게 흔들지 않는 편이 좋습니다."
    elif score >= 45:
        summary = "오늘은 운영 가능한 범위입니다. 할 일을 단순하게 유지하면 무리 없이 갈 수 있습니다."
    else:
        summary = "오늘은 부담 신호가 크게 보입니다. 회복 여지를 먼저 확보하는 편이 좋습니다."

    if request.week_context.weekly_average_score > 0:
        if score >= request.week_context.weekly_average_score + 8:
            summary += " 최근 기준선보다 조금 나은 편입니다."
        elif score <= request.week_context.weekly_average_score - 8:
            summary += " 최근 기준선보다 낮아 기대치를 가볍게 두는 편이 맞습니다."
        else:
            summary += " 최근 기준선과 비슷하므로 강도보다 일관성이 더 중요합니다."

    if metrics.stress >= 8:
        focus = "오늘의 초점은 부담을 더 쌓지 않는 것입니다. 일정 사이 완충 시간을 남겨 두세요."
    elif metrics.sleep_quality <= 4:
        focus = "오늘의 초점은 속도를 조금 낮추는 것입니다. 다음 휴식 구간을 먼저 지키세요."
    elif metrics.energy <= 4:
        focus = "오늘의 초점은 한 가지 중요한 일만 확실히 끝내는 것입니다."
    elif metrics.focus <= 4:
        focus = "오늘의 초점은 일을 짧게 나누고 다음 한 단계만 선명하게 두는 것입니다."
    else:
        focus = "오늘의 초점은 이미 도움이 되는 리듬을 유지하고 불필요한 마찰을 늘리지 않는 것입니다."

    return TodayGuidanceResponse(
        source="fallback",
        model=None,
        summary=summary,
        focus=focus,
        disclaimer="의료 진단이 아닌 생활 관리용 요약입니다.",
    )


def build_weekly_fallback(request: WeeklyReportRequest) -> WeeklyReportResponse:
    average_score = request.average_score

    if average_score >= 70:
        headline = "이번 주는 전반적으로 안정적이어서 평소 루틴을 유지할 여유가 있었습니다."
    elif average_score >= 45:
        headline = "이번 주는 괜찮은 날과 걸리는 날이 섞여 있었습니다."
    else:
        headline = "이번 주는 전체적으로 무거웠습니다. 다음 주에는 회복 여지를 더 확보하는 편이 좋습니다."

    if request.average_stress >= 7:
        pattern = "스트레스 평균이 높게 유지되어 가벼운 계획이 더 안전한 기본값으로 보입니다."
    elif request.average_stress <= 4:
        pattern = "스트레스 평균이 비교적 낮아 리듬을 유지하기 쉬운 주였습니다."
    else:
        pattern = "부담은 완전히 낮지 않았지만 급격한 흔들림은 크지 않았습니다."

    if request.trend == "improving":
        reflection = "최근 흐름은 개선 쪽으로 기울어 있습니다. 도움이 된 루틴을 급하게 바꾸지 않는 편이 좋습니다."
        next_step = "다음 한 걸음은 현재 도움이 되는 앵커를 하나 더 선명하게 고정하는 것입니다."
    elif request.trend == "slipping":
        reflection = "최근 흐름은 내려가는 쪽으로 보입니다. 강도를 줄이고 회복 여지를 의도적으로 확보해야 합니다."
        next_step = "다음 한 걸음은 반복되는 부담 하나를 줄이고 계획 크기를 작게 잡는 것입니다."
    else:
        reflection = "큰 상승이나 하락보다 일관성이 더 중요한 주로 보입니다."
        next_step = "다음 한 걸음은 수면, 휴식, 작업 속도 중 하나를 정해 일주일간 유지해 보는 것입니다."

    return WeeklyReportResponse(
        source="fallback",
        model=None,
        headline=headline,
        pattern=pattern,
        reflection=reflection,
        next_step=next_step,
        disclaimer="의료 진단이 아닌 생활 관리용 요약입니다.",
    )

