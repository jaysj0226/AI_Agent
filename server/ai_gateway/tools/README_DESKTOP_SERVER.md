# Desktop Server Workflow

This folder contains the Windows-side management entry point for the AI gateway
running inside WSL on the desktop server account `samue`.

## Target layout

- Windows repo: `C:\Users\samue\Desktop\jsj\workspace\AI_agent\ai_gateway`
- WSL deploy dir: `/home/samue/apps/condition-coach-ai-gateway`
- systemd service: `condition-coach-ai-gateway`
- Tailscale Serve target: `http://127.0.0.1:8000`

## First-time setup on the desktop

Run from Windows PowerShell or double-click the batch file from Explorer:

```powershell
cd C:\Users\samue\Desktop\jsj\workspace\AI_agent\ai_gateway
tools\manage-ai-gateway.bat setup
```

If you are already inside WSL, use the shell wrapper instead:

```bash
cd /mnt/c/Users/samue/Desktop/JSJ/workspace/AI_agent/ai_gateway
./tools/manage-ai-gateway.sh setup
```

That will:

- copy the gateway code into `/home/samue/apps/condition-coach-ai-gateway`
- create `.venv` in the WSL home directory
- install Python dependencies
- create `.env` from `.env.example` if needed
- install and enable the `systemd` service

After setup, edit the deployed env file in WSL:

```bash
nano /home/samue/apps/condition-coach-ai-gateway/.env
```

Set at least:

```env
APP_SHARED_TOKEN=replace_with_a_long_random_token
OPENAI_API_KEY=your_real_openai_key
```

## Daily operations

```powershell
tools\manage-ai-gateway.bat start
tools\manage-ai-gateway.bat stop
tools\manage-ai-gateway.bat restart
tools\manage-ai-gateway.bat status
tools\manage-ai-gateway.bat logs
tools\manage-ai-gateway.bat deploy
tools\manage-ai-gateway.bat serve-on
tools\manage-ai-gateway.bat serve-status
tools\manage-ai-gateway.bat health
```

WSL equivalents:

```bash
./tools/manage-ai-gateway.sh start
./tools/manage-ai-gateway.sh stop
./tools/manage-ai-gateway.sh restart
./tools/manage-ai-gateway.sh status
./tools/manage-ai-gateway.sh logs
./tools/manage-ai-gateway.sh deploy
./tools/manage-ai-gateway.sh serve-on
./tools/manage-ai-gateway.sh serve-status
./tools/manage-ai-gateway.sh health
```

## Notes

- `setup` and `deploy` keep the running service in `/home/samue`, not under `/mnt/c`.
- `deploy` syncs code from the current Windows repo into the WSL home directory and restarts the service.
- `serve-on` keeps the gateway `tailnet only`. It does not enable Funnel.
- The deployed `.env` is intentionally preserved during code sync.
