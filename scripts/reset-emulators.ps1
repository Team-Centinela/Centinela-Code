# Centinela local emulator reset.
# Tears down the docker-compose stack, rebuilds any service with a `build:`
# directive (ingestion, core-backend, serverless-engine), and brings the stack
# back up from the fresh images. Use this when the running containers have
# stale code -- e.g. after `git checkout` touches a service Dockerfile or
# src/main/java. NOT FOR PRODUCTION. Affects only the local docker-compose
# stack. Volumes are preserved (postgres data survives a reset).
#
# Usage:
#   .\scripts\reset-emulators.ps1           reset + rebuild + up + verify
#   .\scripts\reset-emulators.ps1 -Down     tear down only (no rebuild, no up)
#   .\scripts\reset-emulators.ps1 -NoBuild  down + up only (no rebuild; cache hit)
#   .\scripts\reset-emulators.ps1 -NoVerify reset + rebuild + up, skip verify
#   .\scripts\reset-emulators.ps1 -Help     help

[CmdletBinding()]
param(
    [switch]$Down,
    [switch]$NoBuild,
    [switch]$NoVerify,
    [switch]$Help
)

if ($Help) {
    Get-Content $MyInvocation.MyCommand.Path -TotalCount 12 | Select-String -Pattern '^#' | ForEach-Object { $_.Line -replace '^# ?', '' }
    exit 0
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path "$ScriptDir/..").Path
Set-Location -LiteralPath $RepoRoot

Write-Host ""
Write-Host "Centinela - local emulator reset (NOT FOR PRODUCTION)" -ForegroundColor Cyan
Write-Host "Repo: $RepoRoot" -ForegroundColor DarkGray
Write-Host ""

# Fail fast if Docker is not running.
try {
    docker info *>$null
    if ($LASTEXITCODE -ne 0) { throw "docker info failed" }
} catch {
    Write-Host "  [FAIL] Docker daemon is not reachable. Start Docker Desktop and retry." -ForegroundColor Red
    exit 1
}

Write-Host "[1/4] Tearing down stack (docker compose down)..." -ForegroundColor Yellow
$proc = Start-Process -FilePath "docker" -ArgumentList "compose","down" -NoNewWindow -Wait -PassThru
if ($proc.ExitCode -ne 0) {
    Write-Host "  [FAIL] docker compose down exited $($proc.ExitCode)" -ForegroundColor Red
    exit 1
}
Write-Host "  [PASS] docker compose down completed" -ForegroundColor Green

if ($Down) {
    Write-Host ""
    Write-Host "Stack is down. -Down set; exiting." -ForegroundColor Cyan
    exit 0
}

if (-not $NoBuild) {
    Write-Host ""
    Write-Host "[2/4] Rebuilding service images (docker compose build)..." -ForegroundColor Yellow
    # Only services with a `build:` directive are rebuilt: ingestion, core-backend,
    # serverless-engine. The emulator images (postgres, servicebus, sqledge,
    # floci-az) are pulled on first up and cached; this script does not pull.
    $proc = Start-Process -FilePath "docker" -ArgumentList "compose","build" -NoNewWindow -Wait -PassThru
    if ($proc.ExitCode -ne 0) {
        Write-Host "  [FAIL] docker compose build exited $($proc.ExitCode)" -ForegroundColor Red
        exit 1
    }
    Write-Host "  [PASS] docker compose build completed" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "[2/4] -NoBuild set; skipping image rebuild." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[3/4] Bringing stack up (docker compose up -d --wait)..." -ForegroundColor Yellow
$proc = Start-Process -FilePath "docker" -ArgumentList "compose","up","-d","--wait" -NoNewWindow -Wait -PassThru
if ($proc.ExitCode -ne 0) {
    Write-Host "  [FAIL] docker compose up -d --wait exited $($proc.ExitCode)" -ForegroundColor Red
    exit 1
}
Write-Host "  [PASS] docker compose up -d --wait completed" -ForegroundColor Green

if ($NoVerify) {
    Write-Host ""
    Write-Host "Stack is up. -NoVerify set; skipping verify-emulators." -ForegroundColor Cyan
    exit 0
}

Write-Host ""
Write-Host "[4/4] Running verify-emulators.ps1..." -ForegroundColor Yellow
& "$ScriptDir/verify-emulators.ps1"
exit $LASTEXITCODE
