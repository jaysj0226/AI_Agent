from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "Condition Coach AI Gateway"
    app_env: str = "development"
    app_shared_token: str | None = None
    openai_api_key: str | None = None
    openai_model: str = "gpt-5-mini"
    openai_reasoning_effort: Literal["low", "medium", "high"] = "low"
    openai_timeout_seconds: float = 20.0
    bind_host: str = "0.0.0.0"
    bind_port: int = 8000
    max_diary_chars: int = 600

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
