# Condition Coach AI Gateway

이 서버는 Android 앱과 OpenAI 사이의 게이트웨이입니다.

핵심 원칙:
- OpenAI API 키는 Android 앱에 넣지 않습니다.
- 앱은 이 서버만 호출합니다.
- OpenAI 키가 없거나 모델 호출이 실패해도 같은 JSON 스키마로 fallback 응답을 반환합니다.

## 엔드포인트

- `GET /healthz`
- `GET /v1/status`
- `POST /v1/ai/today-guidance`
- `POST /v1/ai/weekly-report`

## 로컬 실행

```bash
cd server/ai_gateway
python -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## 환경변수

- `OPENAI_API_KEY`: 서버 전용 OpenAI 키
- `OPENAI_MODEL`: 기본 모델 이름. 운영에서는 별칭보다 스냅샷 고정을 권장합니다.
- `OPENAI_REASONING_EFFORT`: `low`, `medium`, `high`
- `BIND_HOST`, `BIND_PORT`: 서버 바인딩 설정

## Android 연결

Android 앱은 `local.properties`에서 다음 값을 읽습니다.

```properties
ai.server.url=https://your-server.example.com
```

임시로 IP를 직접 쓸 수도 있지만, 건강/메모 데이터가 지나가므로 실제 사용은 HTTPS 역방향 프록시 뒤에 두는 편이 맞습니다.

## 서버 배포 초안

서버가 `samue@100.100.211.20`라면 대략 순서는 이렇습니다.

```bash
ssh samue@100.100.211.20
sudo apt update
sudo apt install -y python3-venv nginx
mkdir -p /home/samue/apps/condition-coach-ai-gateway
```

그 뒤 이 디렉터리 내용을 서버로 올리고:

```bash
cd /home/samue/apps/condition-coach-ai-gateway
python3 -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
cp .env.example .env
```

`.env`에 `OPENAI_API_KEY`를 넣고 아래 예시 파일을 사용합니다.

- `deploy/systemd/condition-coach-ai-gateway.service.example`
- `deploy/nginx/condition-coach-ai-gateway.conf.example`

## 운영 메모

- 운영에서는 OpenAI 모델을 별칭 대신 스냅샷으로 고정하는 편이 안전합니다.
- 이 게이트웨이는 기본적으로 상태 비저장(stateless)입니다.
- raw payload 저장은 기본 구현에 넣지 않았습니다.
