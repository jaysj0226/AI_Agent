param(
    [ValidateSet("help", "setup", "deploy", "start", "stop", "restart", "status", "logs", "serve-on", "serve-off", "serve-status", "health", "paths")]
    [string]$Action = "help",
    [string]$LinuxUser = "samue",
    [string]$DeployDir = "/home/samue/apps/condition-coach-ai-gateway",
    [string]$ServiceName = "condition-coach-ai-gateway",
    [string]$Distro = "",
    [int]$Port = 8000,
    [int]$LogLines = 100
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$SourceDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Quote-ForBashSingle {
    param([Parameter(Mandatory = $true)][string]$Value)
    return "'" + ($Value -replace "'", "'""'""'") + "'"
}

function Invoke-WslCommand {
    param(
        [Parameter(Mandatory = $true)][string]$CommandText,
        [switch]$AllowFailure
    )

    $arguments = @()
    if ($Distro) {
        $arguments += "-d"
        $arguments += $Distro
    }
    if ($LinuxUser) {
        $arguments += "-u"
        $arguments += $LinuxUser
    }
    $arguments += "--"
    $arguments += "bash"
    $arguments += "-lc"
    $arguments += $CommandText

    & wsl.exe @arguments
    if (-not $AllowFailure -and $LASTEXITCODE -ne 0) {
        throw "WSL command failed with exit code $LASTEXITCODE."
    }
}

function Wait-ForGatewayReady {
    $healthUri = "http://127.0.0.1:{0}/healthz" -f $Port
    $deadline = (Get-Date).AddSeconds(30)

    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Uri $healthUri -TimeoutSec 2
            if ($response.status -eq "ok") {
                return
            }
        }
        catch {
            Start-Sleep -Seconds 1
        }
    }

    throw "Gateway did not become ready within 30 seconds: $healthUri"
}

function Sync-GatewaySource {
    $quotedSource = Quote-ForBashSingle $SourceDir
    $quotedDeploy = Quote-ForBashSingle $DeployDir
    $command = @'
set -euo pipefail
src=$(wslpath __SOURCE__)
deploy=__DEPLOY__
mkdir -p "$deploy"

if command -v rsync >/dev/null 2>&1; then
  rsync -a --delete \
    --exclude '.env' \
    --exclude '.venv' \
    --exclude '__pycache__' \
    --exclude '*.pyc' \
    --exclude '.pytest_cache' \
    "$src/" "$deploy/"
else
  tmp=$(mktemp -d)
  tar -C "$src" \
    --exclude='.env' \
    --exclude='.venv' \
    --exclude='__pycache__' \
    --exclude='*.pyc' \
    --exclude='.pytest_cache' \
    -cf - . | tar -C "$tmp" -xf -
  find "$deploy" -mindepth 1 -maxdepth 1 ! -name '.env' ! -name '.venv' -exec rm -rf {} +
  cp -a "$tmp"/. "$deploy"/
  rm -rf "$tmp"
fi
'@
    $command = $command.Replace("__SOURCE__", $quotedSource).Replace("__DEPLOY__", $quotedDeploy)
    Invoke-WslCommand $command
}

function Install-ServiceFile {
    $quotedDeploy = Quote-ForBashSingle $DeployDir
    $quotedService = Quote-ForBashSingle $ServiceName
    $quotedUser = Quote-ForBashSingle $LinuxUser
    $command = @'
set -euo pipefail
deploy=__DEPLOY__
service_name=__SERVICE__
user_name=__USER__
tmp=$(mktemp)

cat > "$tmp" <<EOF
[Unit]
Description=Condition Coach AI Gateway
After=network.target

[Service]
Type=simple
User=$user_name
WorkingDirectory=$deploy
EnvironmentFile=$deploy/.env
ExecStart=$deploy/.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port __PORT__
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

sudo install -m 644 "$tmp" "/etc/systemd/system/$service_name.service"
rm -f "$tmp"
sudo systemctl daemon-reload
sudo systemctl enable "$service_name"
'@
    $command = $command.Replace("__DEPLOY__", $quotedDeploy).Replace("__SERVICE__", $quotedService).Replace("__USER__", $quotedUser).Replace("__PORT__", [string]$Port)
    Invoke-WslCommand $command
}

function Setup-Gateway {
    Sync-GatewaySource

    $quotedDeploy = Quote-ForBashSingle $DeployDir
    $command = @'
set -euo pipefail
deploy=__DEPLOY__

if [ ! -d "$deploy/.venv" ]; then
  python3 -m venv "$deploy/.venv"
fi

source "$deploy/.venv/bin/activate"
python -m pip install --upgrade pip
python -m pip install -r "$deploy/requirements.txt"

if [ ! -f "$deploy/.env" ]; then
  cp "$deploy/.env.example" "$deploy/.env"
fi

if grep -q '^BIND_HOST=' "$deploy/.env"; then
  sed -i 's/^BIND_HOST=.*/BIND_HOST=127.0.0.1/' "$deploy/.env"
else
  printf '\nBIND_HOST=127.0.0.1\n' >> "$deploy/.env"
fi

if grep -q '^BIND_PORT=' "$deploy/.env"; then
  sed -i 's/^BIND_PORT=.*/BIND_PORT=__PORT__/' "$deploy/.env"
else
  printf '\nBIND_PORT=__PORT__\n' >> "$deploy/.env"
fi
'@
    $command = $command.Replace("__DEPLOY__", $quotedDeploy).Replace("__PORT__", [string]$Port)
    Invoke-WslCommand $command
    Install-ServiceFile

    Write-Host ""
    Write-Host "Setup complete."
    Write-Host "Edit the server env in WSL if needed:"
    Write-Host "  $DeployDir/.env"
    Write-Host "Then start the service:"
    Write-Host "  tools\manage-ai-gateway.bat start"
}

function Deploy-Gateway {
    Sync-GatewaySource

    $quotedDeploy = Quote-ForBashSingle $DeployDir
    $quotedService = Quote-ForBashSingle $ServiceName
    $command = @'
set -euo pipefail
deploy=__DEPLOY__
service_name=__SERVICE__

if [ ! -d "$deploy/.venv" ]; then
  echo "Missing virtual environment. Run setup first."
  exit 1
fi

source "$deploy/.venv/bin/activate"
python -m pip install -r "$deploy/requirements.txt"
sudo systemctl restart "$service_name"
'@
    $command = $command.Replace("__DEPLOY__", $quotedDeploy).Replace("__SERVICE__", $quotedService)
    Invoke-WslCommand $command
    Wait-ForGatewayReady
    Invoke-ServiceCommand "status"
}

function Invoke-ServiceCommand {
    param([Parameter(Mandatory = $true)][string]$ServiceCommand)
    $quotedService = Quote-ForBashSingle $ServiceName
    if ($ServiceCommand -eq "status") {
        $command = "sudo systemctl status --no-pager -l $quotedService"
    }
    else {
        $command = "sudo systemctl $ServiceCommand $quotedService"
    }
    Invoke-WslCommand $command

    if ($ServiceCommand -in @("start", "restart")) {
        Wait-ForGatewayReady
    }
}

function Show-Logs {
    $quotedService = Quote-ForBashSingle $ServiceName
    $command = "sudo journalctl -u $quotedService -n $LogLines --no-pager"
    Invoke-WslCommand $command
}

function Show-Health {
    Write-Host "Local Windows health:"
    try {
        $response = Invoke-RestMethod -Uri ("http://127.0.0.1:{0}/healthz" -f $Port) -TimeoutSec 5
        $response | ConvertTo-Json -Compress
    }
    catch {
        Write-Host $_.Exception.Message
    }

    Write-Host ""
    Write-Host "Tailscale serve status:"
    & tailscale serve status
    if ($LASTEXITCODE -ne 0) {
        throw "tailscale serve status failed with exit code $LASTEXITCODE."
    }
}

function Show-Paths {
    Write-Host "Windows source: $SourceDir"
    Write-Host "WSL deploy dir: $DeployDir"
    Write-Host "Service name  : $ServiceName"
    Write-Host "Linux user    : $LinuxUser"
    if ($Distro) {
        Write-Host "WSL distro    : $Distro"
    }
    else {
        Write-Host "WSL distro    : default"
    }
}

function Show-Help {
    @"
Condition Coach AI Gateway manager

Usage:
  tools\manage-ai-gateway.bat <action>

Actions:
  setup        Sync code to WSL home, create .venv, install deps, install systemd service
  deploy       Sync code, refresh deps, restart the systemd service
  start        Start the systemd service
  stop         Stop the systemd service
  restart      Restart the systemd service
  status       Show systemd service status without pager
  logs         Show recent journal logs
  serve-on     Enable Tailscale HTTPS proxy to http://127.0.0.1:$Port
  serve-off    Disable Tailscale HTTPS proxy
  serve-status Show Tailscale serve status
  health       Check local healthz and show serve status
  paths        Print source and deployment paths

Defaults:
  Linux user : $LinuxUser
  Deploy dir : $DeployDir
  Service    : $ServiceName

Examples:
  tools\manage-ai-gateway.bat setup
  tools\manage-ai-gateway.bat start
  tools\manage-ai-gateway.bat deploy
  tools\manage-ai-gateway.bat serve-on
"@ | Write-Host
}

switch ($Action) {
    "help" { Show-Help }
    "setup" { Setup-Gateway }
    "deploy" { Deploy-Gateway }
    "start" { Invoke-ServiceCommand "start" }
    "stop" { Invoke-ServiceCommand "stop" }
    "restart" { Invoke-ServiceCommand "restart" }
    "status" { Invoke-ServiceCommand "status" }
    "logs" { Show-Logs }
    "serve-on" {
        & tailscale serve --bg ("http://127.0.0.1:{0}" -f $Port)
        if ($LASTEXITCODE -ne 0) {
            throw "tailscale serve failed with exit code $LASTEXITCODE."
        }
    }
    "serve-off" {
        & tailscale serve --https=443 off
        if ($LASTEXITCODE -ne 0) {
            throw "tailscale serve off failed with exit code $LASTEXITCODE."
        }
    }
    "serve-status" {
        & tailscale serve status
        if ($LASTEXITCODE -ne 0) {
            throw "tailscale serve status failed with exit code $LASTEXITCODE."
        }
    }
    "health" { Show-Health }
    "paths" { Show-Paths }
    default { Show-Help }
}
