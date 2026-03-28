#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PS_SCRIPT_WINDOWS="$(wslpath -w "$SCRIPT_DIR/manage-ai-gateway.ps1")"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$PS_SCRIPT_WINDOWS" "$@"
