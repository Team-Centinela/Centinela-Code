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

printf "\n${YELLOW}[1/5] Booting emulator stack...${RESET}\n"
if docker compose up -d --wait; then
    pass "docker compose up -d --wait completed"
else
    fail "docker compose up -d --wait exited $?"
    exit 1
fi

printf "\n${YELLOW}[2/5] Postgres (postgis/postgis:16-3.4-alpine)...${RESET}\n"
if docker exec centinela-postgres pg_isready -U postgres -d centinela >/dev/null 2>&1; then
    pass "pg_isready"
else
    fail "pg_isready"
fi

schemas=$(docker exec centinela-postgres psql -U postgres -d centinela -tA -c \
    "SELECT count(*) FROM pg_namespace WHERE nspname IN ('oltp','outbox','cases','alerts','auth','reporting','rules_config','triggered_rules','received_messages');" | tr -d ' \r\n')
if [[ "$schemas" == "9" ]]; then
    pass "9 ADR-002 schemas present"
else
    fail "expected 9 schemas, got '$schemas'"
fi

exts=$(docker exec centinela-postgres psql -U postgres -d centinela -tA -c \
    "SELECT count(*) FROM pg_extension WHERE extname IN ('postgis','uuid-ossp');" | tr -d ' \r\n')
if [[ "$exts" == "2" ]]; then
    pass "postgis + uuid-ossp extensions"
else
    fail "expected 2 extensions, got '$exts'"
fi

printf "\n${YELLOW}[3/5] Service Bus Emulator...${RESET}\n"
sb_ok=0
for i in $(seq 1 30); do
    if body=$(curl -fsS http://localhost:5300/health 2>/dev/null); then
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
for q in transactions-raw documents-pending documents-pending-pub fraud-evaluation; do
    if grep -qE "Creating queue:[[:space:]]+${q}\b" <<<"$sb_log"; then
        pass "queue '$q' created"
    else
        fail "queue '$q' NOT in SB Emulator startup log"
    fi
done
for t in case-events fraud-evaluation-events; do
    if grep -qE "Creating topic:[[:space:]]+${t}\b" <<<"$sb_log"; then
        pass "topic '$t' created"
    else
        fail "topic '$t' NOT in SB Emulator startup log"
    fi
done
for s in core-backend-sub ingestion-sub; do
    if grep -qE "Creating subscription[[:space:]]+${s}\b" <<<"$sb_log"; then
        pass "subscription '$s' created"
    else
        fail "subscription '$s' NOT in SB Emulator startup log"
    fi
done

printf "\n${YELLOW}[4/5] Floci-AZ (Blob + KV + AppConfig + Monitor)...${RESET}\n"
floci_ok=0
for i in $(seq 1 15); do
    if body=$(curl -fsS http://localhost:4577/_floci/health 2>/dev/null); then
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

printf "\n${YELLOW}[5/5] SQL Edge (state store for SB Emulator)...${RESET}\n"
if docker exec centinela-sqledge bash -c "timeout 3 bash -c 'echo > /dev/tcp/localhost/1433'" >/dev/null 2>&1; then
    pass "SQL Edge TCP 1433 reachable"
else
    fail "SQL Edge TCP 1433 reachable"
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
