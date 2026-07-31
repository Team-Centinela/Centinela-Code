# Centinela Phase 0.0 + Phase 0.1 emulator verification.
# Issue #167 / PR #171 (§0.0 gate) + #167 §0.1 dry-runs (opt-in via -Phase01).
#
# Sections 1-7 are the §0.0 acceptance gate (always on).
# Sections 8-10 are Phase 0.1 dry-runs (opt-in via -Phase01):
#   8/10  PostGIS V1 migration dry-run
#   9/10  SB Emulator consumer-replay smoke (coarse publisher check)
#   10/10 Floci-AZ wiring smoke (coarse endpoint check)
# Full round-trip tests (AMQP, KV PUT/GET, Blob PUT/GET) are JUnit-based
# and live with their lane owners per #167.

<#
.SYNOPSIS
    Centinela Phase 0.0 + Phase 0.1 emulator verification (issue #167).

.DESCRIPTION
    Boots the Docker Compose emulator stack and asserts every service-owned
    first-schema + queue/topic + Spring Boot actuator endpoint is healthy.
    Sections 1-7 are the §0.0 acceptance gate. Sections 8-10 are Phase 0.1
    dry-runs, opt-in via -Phase01 so the fast path stays within the
    ADR-011 §11.2 cold-start budget (≤3 min).

.PARAMETER Down
    Tear down the compose stack after verification (docker compose down).

.PARAMETER Phase01
    Run the three Phase 0.1 dry-run sections (8/10, 9/10, 10/10).
    Off by default.

.PARAMETER Help
    Show this help.

.EXAMPLE
    .\scripts\verify-emulators.ps1
    Run only the §0.0 acceptance checks (~3 min).

.EXAMPLE
    .\scripts\verify-emulators.ps1 -Phase01
    Run §0.0 + the three Phase 0.1 dry-runs.

.EXAMPLE
    .\scripts\verify-emulators.ps1 -Phase01 -Down
    Run §0.0 + Phase 0.1, then tear down the stack.
#>

[CmdletBinding()]
param(
    [switch]$Down,
    [switch]$Phase01,
    [switch]$Help
)

if ($Help) {
    Get-Help $MyInvocation.MyCommand.Path -Full | Out-Host
    exit 0
}

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
$IngestionPort = if ($env:INGESTION_PORT) { $env:INGESTION_PORT } else { '8081' }
$CoreBackendPort = if ($env:CORE_BACKEND_PORT) { $env:CORE_BACKEND_PORT } else { '8080' }
$ServerlessEnginePort = if ($env:SERVERLESS_ENGINE_PORT) { $env:SERVERLESS_ENGINE_PORT } else { '8082' }

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

Write-Host "[1/10] Booting emulator stack..." -ForegroundColor Yellow
$proc = Start-Process -FilePath "docker" -ArgumentList "compose","up","-d","--wait" -NoNewWindow -Wait -PassThru
if ($proc.ExitCode -ne 0) {
    Write-Fail "docker compose up -d --wait exited $($proc.ExitCode)"
    exit 1
}
Write-Pass "docker compose up -d --wait completed"

Write-Host ""
Write-Host "[2/10] Postgres (postgis/postgis:16-3.4-alpine)..." -ForegroundColor Yellow
docker exec centinela-postgres pg_isready -U postgres -d centinela *>$null
if ($LASTEXITCODE -eq 0) { Write-Pass "pg_isready" } else { Write-Fail "pg_isready" }

# Tracked by #184/#189: detect the final postmaster either by the marker file
# written by init.sql OR by the gap between the container start time and
# pg_postmaster_start_time() (the final postmaster always starts after the
# temporary init postmaster, so its start time is strictly later).
$markerExists = $false
docker exec centinela-postgres test -f /var/lib/postgresql/.centinela-init-complete *>$null
$markerExists = $LASTEXITCODE -eq 0

$postmasterStart = (docker exec centinela-postgres psql -U postgres -d centinela -tA -c "SELECT pg_postmaster_start_time();" 2>$null).Trim()
$containerStart = (docker inspect centinela-postgres --format "{{.State.StartedAt}}" 2>$null).Trim()
$gapSeconds = -1
try {
    $pmStart = [datetime]::Parse($postmasterStart)
    $ctStart = [datetime]::Parse($containerStart)
    $gapSeconds = [int]([math]::Abs(($pmStart - $ctStart).TotalSeconds))
} catch { $gapSeconds = -1 }

# Reused-volume case: init.sql does not run because the data volume already
# has the Postgres cluster initialized. The postmaster starts almost immediately
# (gap < 5s), but the data is already final — service-owned schemas exist.
$tableCount = (docker exec centinela-postgres psql -U postgres -d centinela -tA -c "SELECT count(*) FROM pg_tables WHERE schemaname IN ('oltp','outbox','rules_config','cases');" 2>$null).Trim()
$dataInitialized = ($tableCount -match '^\d+$' -and [int]$tableCount -gt 0)

if ($markerExists) {
    Write-Pass "init.sql marker file present (final postmaster, see #184)"
} elseif ($gapSeconds -ge 5) {
    Write-Pass "postmaster start is ${gapSeconds}s after container start (final postmaster, see #184)"
} elseif ($dataInitialized) {
    Write-Pass "data already initialized (${tableCount} service-owned tables; reused volume, see #184)"
} else {
    Write-Fail "no marker file, no init gap, no initialized data; service-owned schemas may not yet exist (see #184)"
}

$exts = (docker exec centinela-postgres psql -U postgres -d centinela -tA -c "SELECT count(*) FROM pg_extension WHERE extname IN ('postgis','uuid-ossp');" 2>$null).Trim()
if ($exts -eq "2") { Write-Pass "postgis + uuid-ossp extensions" } else { Write-Fail "expected 2 extensions, got '$exts'" }

Write-Host ""
Write-Host "[3/10] Service Bus Emulator..." -ForegroundColor Yellow
$null = Test-HttpStatus -Url "http://localhost:${ServiceBusMgmtPort}/health" -Label "SB Emulator /health"

$sbLog = docker logs centinela-servicebus --tail 2000 2>&1
foreach ($q in 'documents-pending','documents-pending-pub','fraud-evaluation') {
    if ($sbLog -match "Creating queue:\s+$([regex]::Escape($q))\b") { Write-Pass "queue '$q' created" }
    else { Write-Fail "queue '$q' NOT in SB Emulator startup log" }
}
foreach ($t in 'transactions-raw','case-events','fraud-evaluation-events') {
    if ($sbLog -match "Creating topic:\s+$([regex]::Escape($t))\b") { Write-Pass "topic '$t' created" }
    else { Write-Fail "topic '$t' NOT in SB Emulator startup log" }
}
foreach ($s in 'core-backend-sub','ingestion-sub','serverless-engine','core-backend') {
    if ($sbLog -match "Creating subscription\s+$([regex]::Escape($s))\b") { Write-Pass "subscription '$s' created" }
    else { Write-Fail "subscription '$s' NOT in SB Emulator startup log" }
}

Write-Host ""
Write-Host "[4/10] Floci-AZ (Blob + KV + AppConfig + Monitor)..." -ForegroundColor Yellow
$null = Test-HttpStatus -Url "http://localhost:${FlociAzPort}/_floci/health" -Label "Floci-AZ /health" -MaxAttempts 15 -DelaySeconds 2

Write-Host ""
Write-Host "[5/10] SQL Edge (state store for SB Emulator)..." -ForegroundColor Yellow
docker exec centinela-sqledge bash -c "timeout 3 bash -c 'echo > /dev/tcp/localhost/1433'" *>$null
if ($LASTEXITCODE -eq 0) { Write-Pass "SQL Edge TCP 1433 reachable" } else { Write-Fail "SQL Edge TCP 1433 reachable" }

Write-Host ""
Write-Host "[6/10] Spring Boot services (actuator /health)..." -ForegroundColor Yellow
$null = Test-HttpStatus -Url "http://localhost:${IngestionPort}/actuator/health" -Label "ingestion /actuator/health" -MaxAttempts 60 -DelaySeconds 2
$null = Test-HttpStatus -Url "http://localhost:${CoreBackendPort}/actuator/health" -Label "core-backend /actuator/health" -MaxAttempts 60 -DelaySeconds 2
$null = Test-HttpStatus -Url "http://localhost:${ServerlessEnginePort}/actuator/health" -Label "serverless-engine /actuator/health" -MaxAttempts 60 -DelaySeconds 2

$coreBackendRestarts = (docker inspect centinela-core-backend --format "{{.RestartCount}}" 2>$null).Trim()
if ($coreBackendRestarts -eq "0") {
    Write-Pass "core-backend completed first boot without retries (#191)"
} else {
    Write-Fail "core-backend restarted $coreBackendRestarts time(s) during startup (#191)"
}

Write-Host ""
Write-Host "[7/10] Flyway-owned service schemas..." -ForegroundColor Yellow
# Tracked by #189 (and refined after #190): now that all three Spring services
# report /actuator/health = UP, every service's Flyway history must exist in
# the *first* schema listed in its `spring.flyway.schemas` (Flyway writes
# `flyway_schema_history` only to the first schema). The expected first
# schemas are: ingestion=oltp, core-backend=cases, serverless-engine=rules_config.
# The remaining service-owned schemas (`outbox`, `auth`, `alerts`, `reporting`)
# are migrated by the same Flyway run but share the first-schema history table.
$expected = @('oltp','cases','rules_config')
$rawList = docker exec centinela-postgres psql -U postgres -d centinela -tA -c "SELECT schemaname FROM pg_tables WHERE tablename = 'flyway_schema_history' ORDER BY schemaname;" 2>$null
$present = @()
if ($rawList) {
    foreach ($line in ($rawList -split "`n")) {
        $trimmed = $line.Trim()
        if ($trimmed) { $present += $trimmed }
    }
}

$missing = @($expected | Where-Object { $present -notcontains $_ })
if ($missing.Count -eq 0) {
    $matching = @($present | Where-Object { $expected -contains $_ })
    Write-Pass ("Flyway schema history present in " + $matching.Count + " service-owned first-schemas (#182/#189)")
} else {
    Write-Fail ("missing Flyway schema history for: " + ($missing -join ', '))
}

if ($Phase01) {
    Write-Host ""
    Write-Host "[8/10] PostGIS V1 migration dry-run (Phase 0.1 - opt-in)..." -ForegroundColor Yellow
    foreach ($svc in @('oltp','cases','rules_config')) {
        $ok = (docker exec centinela-postgres psql -U postgres -d centinela -tA -c "SELECT count(*) FROM ${svc}.flyway_schema_history WHERE success = true;" 2>$null).Trim()
        $failCount = (docker exec centinela-postgres psql -U postgres -d centinela -tA -c "SELECT count(*) FROM ${svc}.flyway_schema_history WHERE success = false;" 2>$null).Trim()
        if ($ok -match '^\d+$' -and $failCount -match '^\d+$' -and [int]$ok -gt 0 -and [int]$failCount -eq 0) {
            Write-Pass "Flyway V1+ clean in $svc ($ok successful, 0 failed)"
        } elseif ($ok -match '^\d+$' -and [int]$ok -eq 0) {
            Write-Fail "Flyway state in ${svc}: 0 successful migrations (no V1 applied?)"
        } else {
            Write-Fail "Flyway state in ${svc}: ok=$ok fail=$failCount (expected ok>0 fail=0)"
        }
    }

    Write-Host ""
    Write-Host "[9/10] SB Emulator consumer-replay smoke (Phase 0.1 - opt-in)..." -ForegroundColor Yellow
    $publishedRecent = (docker exec centinela-postgres psql -U postgres -d centinela -tA -c "SELECT count(*) FROM outbox.outbox_events WHERE status = 'PUBLISHED' AND published_at > NOW() - INTERVAL '10 minutes';" 2>$null).Trim()
    $pendingStale = (docker exec centinela-postgres psql -U postgres -d centinela -tA -c "SELECT count(*) FROM outbox.outbox_events WHERE status = 'PENDING' AND created_at < NOW() - INTERVAL '60 seconds';" 2>$null).Trim()
    if ($pendingStale -match '^\d+$' -and [int]$pendingStale -eq 0) {
        Write-Pass "no stale PENDING outbox rows (publisher not stuck)"
    } else {
        Write-Fail "$pendingStale PENDING outbox rows older than 60s (publisher stuck?)"
    }
    if ($publishedRecent -match '^\d+$' -and [int]$publishedRecent -gt 0) {
        Write-Pass "$publishedRecent outbox events published in last 10 min (AMQP publish path observed)"
    } else {
        Write-Info "no outbox events published in last 10 min (no traffic; AMQP topology covered by [3/10] and [6/10])"
    }

    Write-Host ""
    Write-Host "[10/10] Floci-AZ wiring smoke (Phase 0.1 - opt-in)..." -ForegroundColor Yellow
    # Each entry: name, expected-path, expected-class ("wired" returns 2xx or 401,
    # "stubbed" returns 501 = routed but not implemented yet).
    $flociChecks = @(
        @{ Name = 'Key Vault'; Path = "/devstoreaccount1-keyvault/?api-version=7.4"; Expected = 'wired' },
        @{ Name = 'Blob';      Path = "/devstoreaccount1/?comp=list";                Expected = 'wired' },
        @{ Name = 'Monitor';   Path = "/dataCollectionRules";                        Expected = 'stubbed' },
        @{ Name = 'AppConfig'; Path = "/appconfig";                                  Expected = 'stubbed' },
        @{ Name = 'Logs';      Path = "/v1/logs";                                    Expected = 'stubbed' }
    )
    foreach ($check in $flociChecks) {
        $name = $check.Name
        $path = $check.Path
        $expected = $check.Expected
        $code = 0
        try {
            $r = Invoke-WebRequest -Uri "http://localhost:${FlociAzPort}${path}" -Method Get -UseBasicParsing -ErrorAction Stop
            $code = [int]$r.StatusCode
        } catch {
            if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode } else { $code = 0 }
        }
        if ($expected -eq 'wired' -and ($code -eq 200 -or $code -eq 401)) {
            Write-Pass "Floci-AZ $name wired (HTTP $code)"
        } elseif ($expected -eq 'stubbed' -and $code -eq 501) {
            Write-Info "Floci-AZ $name stubbed (HTTP 501 = route present, impl pending)"
        } elseif ($code -eq 0) {
            Write-Fail "Floci-AZ $name unreachable (connection reset on $path)"
        } else {
            Write-Fail "Floci-AZ $name returned HTTP $code on $path (expected $expected)"
        }
    }
}

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
