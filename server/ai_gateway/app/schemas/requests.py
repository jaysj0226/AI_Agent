from typing import Literal

from pydantic import BaseModel, Field, field_validator


class TodayMetrics(BaseModel):
    sleep_quality: int = Field(ge=1, le=10)
    energy: int = Field(ge=1, le=10)
    stress: int = Field(ge=1, le=10)
    focus: int = Field(ge=1, le=10)


class TodayWeekContext(BaseModel):
    entry_count_last_7_days: int = Field(ge=0, le=7)
    weekly_average_score: int = Field(ge=0, le=100)
    trend: Literal["improving", "stable", "slipping", "building"] = "building"


class TodayHealthContext(BaseModel):
    provider_available: bool = False
    permissions_granted: bool = False


class TodayGuidanceRequest(BaseModel):
    locale: str = "ko-KR"
    deterministic_score: int = Field(ge=0, le=100)
    score_band: Literal["good", "fair", "low", "building"]
    metrics: TodayMetrics
    week_context: TodayWeekContext
    diary_note: str = Field(default="", max_length=600)
    health_context: TodayHealthContext = Field(default_factory=TodayHealthContext)

    @field_validator("diary_note")
    @classmethod
    def trim_diary_note(cls, value: str) -> str:
        return value.strip()


class WeeklyRecentEntry(BaseModel):
    date_label: str = Field(min_length=1, max_length=20)
    score: int = Field(ge=0, le=100)
    note: str = Field(default="", max_length=200)

    @field_validator("note")
    @classmethod
    def trim_note(cls, value: str) -> str:
        return value.strip()


class WeeklyReportRequest(BaseModel):
    locale: str = "ko-KR"
    average_score: int = Field(ge=0, le=100)
    trend: Literal["improving", "stable", "slipping", "building"] = "building"
    entry_count_last_7_days: int = Field(ge=0, le=7)
    average_stress: int = Field(ge=0, le=10)
    recent_entries: list[WeeklyRecentEntry] = Field(default_factory=list, max_length=5)

