# Centinela Phase 0.0 emulator verification.
# Issue #167 / PR #171. Phase 0.0 acceptance checklist, automated.
#
# Usage:
#   .\scripts\verify-emulators.ps1           verify only, leave containers running
#   .\scripts\verify-emulators.ps1 -Down     verify then docker compose down
#   .\scripts\verify-emulators.ps1 -Help     help

[CmdletBinding()]
param(
    [switch]$Down
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path "$ScriptDir/..").Path
Set-Location -LiteralPath $RepoRoot

$EnvFile = Join-Path $RepoRoot '.env'
if (Test-Path -LiteralPath $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        if ($_ -match '^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.+?)\s*$') {
            $name = $Matches[1]
            $value = $Matches[2] -replace '^["''](.*)["'']$', '$1'
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

$PostgresPort = if ($env:POSTGRES_PORT) { $env:POSTGRES_PORT } else { '5432' }
$ServiceBusAmqpPort = if ($env:SERVICEBUS_AMQP_PORT) { $env:SERVICEBUS_AMQP_PORT } else { '5672' }
$ServiceBusMgmtPort = if ($env:SERVICEBUS_MGMT_PORT) { $env:SERVICEBUS_MGMT_PORT } else { '5300' }
$SqlEdgePort = if ($env:SQLEDGE_PORT) { $env:SQLEDGE_PORT } else { '1433' }
$FlociAzPort = if ($env:FLOCI_AZ_PORT) { $env:FLOCI_AZ_PORT } else { '4577' }

$Script:Curl = 'curl.exe'
$Pass = 0
$Fail = 0

function Write-Pass($msg) { $script:Pass++; Write-Host "  [PASS] $msg" -ForegroundColor Green }
function Write-Fail($msg) { $script:Fail++; Write-Host "  [FAIL] $msg" -ForegroundColor Red }
function Write-Info($msg) { Write-Host "  [..]  $msg" -ForegroundColor DarkGray }

function Test-HttpStatus {
    param([string]$Url, [string]$Label, [int]$MaxAttempts = 30, [int]$DelaySeconds = 2)
    for ($i = 1; $i -le $MaxAttempts; $i++) {
        try {
            $body = & $Script:Curl -fsS $Url 2>$null
            if ($body) {
                Write-Pass "$Label -> $body"
                return $true
            }
        } catch {
            # not ready yet
        }
        Write-Info "Waiting for $Label (attempt $i/$MaxAttempts)..."
        Start-Sleep -Seconds $DelaySeconds
    }
    Write-Fail "$Label did not become healthy in $($MaxAttempts * $DelaySeconds)s"
    return $false
}

Write-Host ""
Write-Host "Centinela - Phase 0.0 emulator verification" -ForegroundColor Cyan
Write-Host "Repo: $RepoRoot" -ForegroundColor DarkGray
Write-Host ""

Write-Host "[1/5] Booting emulator stack..." -ForegroundColor Yellow
$proc = Start-Process -FilePath "docker" -ArgumentList "compose","up","-d","--wait" -NoNewWindow -Wait -PassThru
if ($proc.ExitCode -ne 0) {
    Write-Fail "docker compose up -d --wait exited $($proc.ExitCode)"
    exit 1
}
Write-Pass "docker compose up -d --wait completed"

Write-Host ""
Write-Host "[2/5] Postgres (postgis/postgis:16-3.4-alpine)..." -ForegroundColor Yellow
docker exec centinela-postgres pg_isready -U postgres -d centinela *>$null
if ($LASTEXITCODE -eq 0) { Write-Pass "pg_isready" } else { Write-Fail "pg_isready" }

$schemas = (docker exec centinela-postgres psql -U postgres -d centinela -tA -c "SELECT count(*) FROM pg_namespace WHERE nspname IN ('oltp','outbox','cases','alerts','auth','reporting','rules_config','triggered_rules','received_messages');" 2>$null).Trim()
if ($schemas -eq "9") { Write-Pass "9 ADR-002 schemas present" } else { Write-Fail "expected 9 schemas, got '$schemas'" }

$exts = (docker exec centinela-postgres psql -U postgres -d centinela -tA -c "SELECT count(*) FROM pg_extension WHERE extname IN ('postgis','uuid-ossp');" 2>$null).Trim()
if ($exts -eq "2") { Write-Pass "postgis + uuid-ossp extensions" } else { Write-Fail "expected 2 extensions, got '$exts'" }

Write-Host ""
Write-Host "[3/5] Service Bus Emulator..." -ForegroundColor Yellow
$null = Test-HttpStatus -Url "http://localhost:${ServiceBusMgmtPort}/health" -Label "SB Emulator /health"

$sbLog = docker logs centinela-servicebus --tail 2000 2>&1
foreach ($q in 'transactions-raw','documents-pending','documents-pending-pub','fraud-evaluation') {
    if ($sbLog -match "Creating queue:\s+$([regex]::Escape($q))\b") { Write-Pass "queue '$q' created" }
    else { Write-Fail "queue '$q' NOT in SB Emulator startup log" }
}
foreach ($t in 'case-events','fraud-evaluation-events') {
    if ($sbLog -match "Creating topic:\s+$([regex]::Escape($t))\b") { Write-Pass "topic '$t' created" }
    else { Write-Fail "topic '$t' NOT in SB Emulator startup log" }
}
foreach ($s in 'core-backend-sub','ingestion-sub') {
    if ($sbLog -match "Creating subscription\s+$([regex]::Escape($s))\b") { Write-Pass "subscription '$s' created" }
    else { Write-Fail "subscription '$s' NOT in SB Emulator startup log" }
}

Write-Host ""
Write-Host "[4/5] Floci-AZ (Blob + KV + AppConfig + Monitor)..." -ForegroundColor Yellow
$null = Test-HttpStatus -Url "http://localhost:${FlociAzPort}/_floci/health" -Label "Floci-AZ /health" -MaxAttempts 15 -DelaySeconds 2

Write-Host ""
Write-Host "[5/5] SQL Edge (state store for SB Emulator)..." -ForegroundColor Yellow
docker exec centinela-sqledge bash -c "timeout 3 bash -c 'echo > /dev/tcp/localhost/1433'" *>$null
if ($LASTEXITCODE -eq 0) { Write-Pass "SQL Edge TCP 1433 reachable" } else { Write-Fail "SQL Edge TCP 1433 reachable" }

Write-Host ""
Write-Host "==============================" -ForegroundColor Cyan
$color = if ($Fail -eq 0) { 'Green' } else { 'Red' }
Write-Host "Result: $Pass PASS / $Fail FAIL" -ForegroundColor $color
Write-Host "==============================" -ForegroundColor Cyan

if ($Fail -gt 0) { exit 1 }

if ($Down) {
    Write-Host ""
    Write-Host "Tearing down stack (-Down flag)..." -ForegroundColor Yellow
    docker compose down
    exit $LASTEXITCODE
}

exit 0
