#!/usr/bin/env bash
# Centinela Phase 0.0 emulator verification.
# Issue #167 / PR #171. Phase 0.0 acceptance checklist, automated.
#
# Usage:
#   ./scripts/verify-emulators.sh           verify only, leave containers running
#   ./scripts/verify-emulators.sh --down    verify then docker compose down
#   ./scripts/verify-emulators.sh --help    help

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

if [[ -f "$REPO_ROOT/.env" ]]; then
    set -a
    source "$REPO_ROOT/.env"
    set +a
fi

POSTGRES_PORT=${POSTGRES_PORT:-5432}
SERVICEBUS_AMQP_PORT=${SERVICEBUS_AMQP_PORT:-5672}
SERVICEBUS_MGMT_PORT=${SERVICEBUS_MGMT_PORT:-5300}
SQLEDGE_PORT=${SQLEDGE_PORT:-1433}
FLOCI_AZ_PORT=${FLOCI_AZ_PORT:-4577}
INGESTION_PORT=${INGESTION_PORT:-8081}
CORE_BACKEND_PORT=${CORE_BACKEND_PORT:-8080}
SERVERLESS_ENGINE_PORT=${SERVERLESS_ENGINE_PORT:-8082}

if [[ -t 1 ]]; then
    RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; CYAN=$'\033[36m'; GRAY=$'\033[90m'; RESET=$'\033[0m'
else
    RED=""; GREEN=""; YELLOW=""; CYAN=""; GRAY=""; RESET=""
fi

PASS=0
FAIL=0
pass() { PASS=$((PASS+1)); printf "  ${GREEN}[PASS]${RESET} %s\n" "$1"; }
fail() { FAIL=$((FAIL+1)); printf "  ${RED}[FAIL]${RESET} %s\n" "$1"; }
info() { printf "  ${GRAY}[..]${RESET}  %s\n" "$1"; }

TEARDOWN=0
for arg in "$@"; do
    case "$arg" in
        --down|-d) TEARDOWN=1 ;;
        --help|-h)
            cat <<EOF
Centinela Phase 0.0 emulator verification.

Usage:
  $0              verify only, leave containers running
  $0 --down       verify then docker compose down
  $0 --help       this help
EOF
            exit 0
            ;;
        *) fail "Unknown argument: $arg"; exit 1 ;;
    esac
done

printf "\n${YELLOW}[1/7] Booting emulator stack...${RESET}\n"
if docker compose up -d --wait; then
    pass "docker compose up -d --wait completed"
else
    fail "docker compose up -d --wait exited $?"
    exit 1
fi

printf "\n${YELLOW}[2/7] Postgres (postgis/postgis:16-3.4-alpine)...${RESET}\n"
if docker exec centinela-postgres pg_isready -U postgres -d centinela >/dev/null 2>&1; then
    pass "pg_isready"
else
    fail "pg_isready"
fi

# Tracked by #184/#189: detect the final postmaster either by the marker file
# written by init.sql OR by the gap between the container start time and
# pg_postmaster_start_time() (the final postmaster always starts after the
# temporary init postmaster).
if docker exec centinela-postgres sh -c 'test -f /var/lib/postgresql/.centinela-init-complete' >/dev/null 2>&1; then
    pass "init.sql marker file present (final postmaster, see #184)"
else
    postmaster_start=$(docker exec centinela-postgres psql -U postgres -d centinela -tA -c "SELECT pg_postmaster_start_time();" | tr -d '\r\n')
    container_start=$(docker inspect centinela-postgres --format '{{.State.StartedAt}}' | tr -d '\r\n')
    # Reused-volume case: init.sql does not run because the data volume already
    # has the Postgres cluster initialized. The postmaster starts almost immediately
    # (gap < 5s), but the data is already final -- service-owned schemas exist.
    table_count=$(docker exec centinela-postgres psql -U postgres -d centinela -tA -c \
        "SELECT count(*) FROM pg_tables WHERE schemaname IN ('oltp','outbox','rules_config','cases');" | tr -d ' \r\n')
    if [[ -n "$postmaster_start" && -n "$container_start" ]]; then
        pm_epoch=$(date -u -d "$postmaster_start" +%s 2>/dev/null || echo 0)
        ct_epoch=$(date -u -d "$container_start" +%s 2>/dev/null || echo 0)
        if [[ "$pm_epoch" -gt 0 && "$ct_epoch" -gt 0 ]]; then
            gap=$(( pm_epoch - ct_epoch ))
            if [[ $gap -ge 5 ]]; then
                pass "postmaster start is ${gap}s after container start (final postmaster, see #184)"
            elif [[ "$table_count" =~ ^[0-9]+$ ]] && [[ $table_count -gt 0 ]]; then
                pass "data already initialized (${table_count} service-owned tables; reused volume, see #184)"
            else
                fail "no marker file, no init gap, no initialized data; service-owned schemas may not yet exist (see #184)"
            fi
        else
            fail "could not determine postmaster/container start times"
        fi
    else
        fail "could not read postmaster or container start time"
    fi
fi

exts=$(docker exec centinela-postgres psql -U postgres -d centinela -tA -c \
    "SELECT count(*) FROM pg_extension WHERE extname IN ('postgis','uuid-ossp');" | tr -d ' \r\n')
if [[ "$exts" == "2" ]]; then
    pass "postgis + uuid-ossp extensions"
else
    fail "expected 2 extensions, got '$exts'"
fi

printf "\n${YELLOW}[3/7] Service Bus Emulator...${RESET}\n"
sb_ok=0
for i in $(seq 1 30); do
    if body=$(curl -fsS "http://localhost:${SERVICEBUS_MGMT_PORT}/health" 2>/dev/null); then
        printf "  ${GREEN}[PASS]${RESET} SB Emulator /health -> %s\n" "$body"
        PASS=$((PASS+1))
        sb_ok=1
        break
    fi
    info "Waiting for SB Emulator /health (attempt $i/30)..."
    sleep 2
done
if [[ $sb_ok -eq 0 ]]; then
    fail "SB Emulator /health did not become healthy in 60s"
fi

sb_log=$(docker logs centinela-servicebus --tail 2000 2>&1 || true)
for q in documents-pending documents-pending-pub fraud-evaluation; do
    if grep -qE "Creating queue:[[:space:]]+${q}\b" <<<"$sb_log"; then
        pass "queue '$q' created"
    else
        fail "queue '$q' NOT in SB Emulator startup log"
    fi
done
for t in transactions-raw case-events fraud-evaluation-events; do
    if grep -qE "Creating topic:[[:space:]]+${t}\b" <<<"$sb_log"; then
        pass "topic '$t' created"
    else
        fail "topic '$t' NOT in SB Emulator startup log"
    fi
done
for s in core-backend-sub ingestion-sub serverless-engine core-backend; do
    if grep -qE "Creating subscription[[:space:]]+${s}\b" <<<"$sb_log"; then
        pass "subscription '$s' created"
    else
        fail "subscription '$s' NOT in SB Emulator startup log"
    fi
done

printf "\n${YELLOW}[4/7] Floci-AZ (Blob + KV + AppConfig + Monitor)...${RESET}\n"
floci_ok=0
for i in $(seq 1 15); do
    if body=$(curl -fsS "http://localhost:${FLOCI_AZ_PORT}/_floci/health" 2>/dev/null); then
        printf "  ${GREEN}[PASS]${RESET} Floci-AZ /health -> %s\n" "$body"
        PASS=$((PASS+1))
        floci_ok=1
        break
    fi
    info "Waiting for Floci-AZ /health (attempt $i/15)..."
    sleep 2
done
if [[ $floci_ok -eq 0 ]]; then
    fail "Floci-AZ /health did not become healthy in 30s"
fi

printf "\n${YELLOW}[5/7] SQL Edge (state store for SB Emulator)...${RESET}\n"
if docker exec centinela-sqledge bash -c "timeout 3 bash -c 'echo > /dev/tcp/localhost/1433'" >/dev/null 2>&1; then
    pass "SQL Edge TCP 1433 reachable"
else
    fail "SQL Edge TCP 1433 reachable"
fi

printf "\n${YELLOW}[6/7] Spring Boot services (actuator /health)...${RESET}\n"
for svc_port in "ingestion ${INGESTION_PORT}" "core-backend ${CORE_BACKEND_PORT}" "serverless-engine ${SERVERLESS_ENGINE_PORT}"; do
    name=$(echo "$svc_port" | awk '{print $1}')
    port=$(echo "$svc_port" | awk '{print $2}')
    svc_ok=0
    for i in $(seq 1 60); do
        if body=$(curl -fsS "http://localhost:${port}/actuator/health" 2>/dev/null); then
            printf "  ${GREEN}[PASS]${RESET} %s /actuator/health -> %s\n" "$name" "$body"
            PASS=$((PASS+1))
            svc_ok=1
            break
        fi
        info "Waiting for ${name} /actuator/health (attempt $i/60)..."
        sleep 2
    done
    if [[ $svc_ok -eq 0 ]]; then
        fail "${name} /actuator/health did not become healthy in 120s"
    fi
done

core_backend_restarts=$(docker inspect centinela-core-backend --format '{{.RestartCount}}' | tr -d ' \r\n')
if [[ "$core_backend_restarts" == "0" ]]; then
    pass "core-backend completed first boot without retries (#191)"
else
    fail "core-backend restarted ${core_backend_restarts} time(s) during startup (#191)"
fi

printf "\n${YELLOW}[7/7] Flyway-owned service schemas...${RESET}\n"
# Tracked by #189 (and refined after #190): now that all three Spring services
# report /actuator/health = UP, every service's Flyway history must exist in
# the *first* schema listed in its `spring.flyway.schemas` (Flyway writes
# `flyway_schema_history` only to the first schema). The expected first
# schemas are: ingestion=oltp, core-backend=cases, serverless-engine=rules_config.
# The remaining service-owned schemas (`outbox`, `auth`, `alerts`, `reporting`)
# are migrated by the same Flyway run but share the first-schema history table.
expected=(oltp cases rules_config)
present=$(docker exec centinela-postgres psql -U postgres -d centinela -tA -c \
    "SELECT schemaname FROM pg_tables WHERE tablename = 'flyway_schema_history' ORDER BY schemaname;" | tr -d ' \r')
missing=()
for s in "${expected[@]}"; do
    if ! grep -Fxq "$s" <<<"$present"; then
        missing+=("$s")
    fi
done
if [[ ${#missing[@]} -eq 0 ]]; then
    count=${#expected[@]}
    pass "Flyway schema history present in $count service-owned first-schemas (#182/#189)"
else
    fail "missing Flyway schema history for: ${missing[*]}"
fi

printf "\n${CYAN}==============================${RESET}\n"
if [[ $FAIL -eq 0 ]]; then
    printf "${GREEN}Result: %d PASS / %d FAIL${RESET}\n" "$PASS" "$FAIL"
else
    printf "${RED}Result: %d PASS / %d FAIL${RESET}\n" "$PASS" "$FAIL"
fi
printf "${CYAN}==============================${RESET}\n"

if [[ $FAIL -gt 0 ]]; then
    exit 1
fi

if [[ $TEARDOWN -eq 1 ]]; then
    printf "\n${YELLOW}Tearing down stack (--down flag)...${RESET}\n"
    docker compose down
    exit $?
fi

exit 0
