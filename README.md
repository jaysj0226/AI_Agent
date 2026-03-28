# Condition Coach

> Galaxy Watch & Android 기반 컨디션 관리 + AI 코칭 앱

사용자의 수면, 에너지, 스트레스, 집중력 데이터를 수집하고, AI Agent가 맞춤형 컨디션 관리 제안을 제공합니다.

---

## 주요 기능

### 컨디션 체크인
- 수면 품질, 에너지, 스트레스, 집중력을 1~10점으로 기록
- 자유 텍스트 일기(메모) 작성
- 하루 한 번 체크인, 동일 날짜 업데이트 지원

### 컨디션 점수 & 분석
- 4가지 지표 기반 종합 점수 자동 산출 (0~100)
- 점수 밴드: **Good** (70+) / **Fair** (45~69) / **Low** (~44)
- 7일 추세 분석 (개선 / 안정 / 하락)

### AI Agent 코칭
- **오늘의 가이드**: 최신 체크인 기반 맞춤 요약 + 실천 제안
- **주간 리포트**: 7일간 패턴 분석, 회고, 다음 주 실천 목표
- OpenAI API를 활용한 한국어 코칭
- AI 서버 미연결 시 규칙 기반 로컬 가이던스 자동 제공

### Health Connect 연동
- Google Health Connect를 통해 수면, 걸음 수 데이터 자동 수집
- Galaxy Watch / Pixel Watch 등 Wear OS 기기 데이터 통합
- 선택적 연동 (미연결 시에도 앱 정상 동작)

---

## 아키텍처

```
┌─────────────────────┐     ┌──────────────────────┐
│   Android App       │     │   AI Gateway Server   │
│                     │     │                       │
│  ┌───────────────┐  │     │  ┌─────────────────┐  │
│  │ Today Screen  │──┼──POST──▶ /v1/ai/today    │  │
│  │ Weekly Report │──┼──POST──▶ /v1/ai/weekly   │  │
│  └───────────────┘  │     │  └────────┬────────┘  │
│                     │     │           │           │
│  ┌───────────────┐  │     │  ┌────────▼────────┐  │
│  │ Check-In      │  │     │  │  OpenAI API     │  │
│  │ Settings      │  │     │  │  (gpt-4o-mini)  │  │
│  └───────────────┘  │     │  └─────────────────┘  │
│                     │     │                       │
│  ┌───────────────┐  │     │  Fallback Writer      │
│  │ Health Connect│  │     │  (AI 미연결 시 로컬)    │
│  │ (Galaxy Watch)│  │     │                       │
│  └───────────────┘  │     └──────────────────────┘
└─────────────────────┘
```

| 계층 | 기술 |
|------|------|
| Android App | Java, AndroidX, Navigation Component, Material Design 3 |
| Health 연동 | Health Connect Client 1.0 (Wear OS / Galaxy Watch) |
| AI Server | Python, FastAPI, Uvicorn |
| AI Model | OpenAI API (모델 설정 가능) |
| 네트워크 | Tailscale VPN, HTTPS, Token 인증 |

---

## 화면 구성

| 탭 | 설명 |
|----|------|
| **오늘** | 종합 점수, 지표 그리드, AI 요약 & 실천 제안, Health Connect 상태 |
| **체크인** | 4가지 지표 슬라이더 + 메모 입력 → 저장 |
| **주간 리포트** | 월별 캘린더 뷰, 일별 점수, AI 주간 분석 |
| **설정** | Health Connect 권한, AI 서버 상태, 데이터 관리 |

---

## 프로젝트 구조

```
AI_Agent/
├── app/                          # Android 앱 모듈
│   └── src/main/java/.../
│       ├── ai/                   # AI 서버 통신 클라이언트
│       ├── data/                 # 로컬 데이터 저장소
│       ├── domain/               # 점수 계산, 가이던스 엔진
│       ├── health/               # Health Connect 연동
│       └── ui/                   # Fragment, Formatter
│           ├── today/
│           ├── checkin/
│           ├── report/
│           └── settings/
│
├── server/ai_gateway/            # AI Gateway 서버
│   ├── app/
│   │   ├── api/routes.py         # API 엔드포인트
│   │   ├── services/             # AI 서비스, 폴백 로직
│   │   ├── prompts/wellness.py   # AI 프롬프트 (한국어)
│   │   └── schemas/              # 요청/응답 스키마
│   ├── deploy/                   # systemd 서비스 설정
│   └── requirements.txt
│
└── gradle/                       # Gradle 빌드 설정
```

---

## 설치 & 실행

### Android 앱

**요구사항**: Android Studio, JDK 17+, Android SDK 34

1. 프로젝트 클론
   ```bash
   git clone https://github.com/jaysj0226/AI_Agent.git
   ```

2. `local.properties` 생성 (프로젝트 루트)
   ```properties
   sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk

   # AI 서버 설정 (선택)
   ai.server.base.url=https://your-server-url
   ai.server.shared.token=your-token
   ai.server.timeout.ms=25000
   ```

3. Android Studio에서 프로젝트 열기 → Run

### AI Gateway 서버

**요구사항**: Python 3.11+

1. 가상환경 생성 & 의존성 설치
   ```bash
   cd server/ai_gateway
   python -m venv .venv
   source .venv/bin/activate   # Windows: .venv\Scripts\activate
   pip install -r requirements.txt
   ```

2. `.env` 파일 생성 (`.env.example` 참고)
   ```env
   OPENAI_API_KEY=sk-...
   OPENAI_MODEL=gpt-4o-mini
   APP_SHARED_TOKEN=your-token
   BIND_HOST=0.0.0.0
   BIND_PORT=8000
   ```

3. 서버 실행
   ```bash
   uvicorn app.main:app --host 0.0.0.0 --port 8000
   ```

---

## API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/healthz` | 서버 헬스체크 |
| `GET` | `/v1/status` | 서버 상태 (모델 정보) |
| `POST` | `/v1/ai/today-guidance` | 오늘의 AI 코칭 |
| `POST` | `/v1/ai/weekly-report` | 주간 AI 리포트 |

---

## 기술적 특징

- **하이브리드 가이던스**: AI 서버 연결 시 OpenAI 기반 코칭, 미연결 시 규칙 기반 로컬 가이던스 자동 전환
- **스마트 캐싱**: 클라이언트(메모리) + 서버(10분 TTL) 이중 캐시로 불필요한 API 호출 방지
- **요청 중복 방지**: Generation guard 패턴으로 stale 응답 무시 및 in-flight 요청 관리
- **Health Connect**: Galaxy Watch, Pixel Watch 등 Wear OS 기기의 건강 데이터를 자동 통합
- **한국어 최적화**: 모든 AI 프롬프트 및 UI가 한국어로 설계

---

## 라이선스

MIT License - 자세한 내용은 [LICENSE](LICENSE) 파일을 참고하세요.
