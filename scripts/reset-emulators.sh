#!/usr/bin/env bash
# Centinela local emulator reset.
# Tears down the docker-compose stack, rebuilds any service with a `build:`
# directive (ingestion, core-backend, serverless-engine), and brings the stack
# back up from the fresh images. Use this when the running containers have
# stale code -- e.g. after `git checkout` touches a service Dockerfile or
# src/main/java. NOT FOR PRODUCTION. Affects only the local docker-compose
# stack. Volumes are preserved (postgres data survives a reset).
#
# Usage:
#   ./scripts/reset-emulators.sh           reset + rebuild + up + verify
#   ./scripts/reset-emulators.sh --down    tear down only (no rebuild, no up)
#   ./scripts/reset-emulators.sh --no-build  down + up only (no rebuild; cache hit)
#   ./scripts/reset-emulators.sh --no-verify reset + rebuild + up, skip verify
#   ./scripts/reset-emulators.sh --help    help

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

if [[ -t 1 ]]; then
    RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; CYAN=$'\033[36m'; GRAY=$'\033[90m'; RESET=$'\033[0m'
else
    RED=""; GREEN=""; YELLOW=""; CYAN=""; GRAY=""; RESET=""
fi

TEARDOWN=0
NO_BUILD=0
NO_VERIFY=0
for arg in "$@"; do
    case "$arg" in
        --down|-d)       TEARDOWN=1 ;;
        --no-build)      NO_BUILD=1 ;;
        --no-verify)     NO_VERIFY=1 ;;
        --help|-h)
            sed -n '2,12p' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        *) printf "${RED}Unknown argument: %s${RESET}\n" "$arg"; exit 1 ;;
    esac
done

printf "\n${CYAN}Centinela - local emulator reset (NOT FOR PRODUCTION)${RESET}\n"
printf "${GRAY}Repo: %s${RESET}\n\n" "$REPO_ROOT"

# Fail fast if Docker is not running.
if ! docker info >/dev/null 2>&1; then
    printf "${RED}  [FAIL] Docker daemon is not reachable. Start Docker Desktop and retry.${RESET}\n"
    exit 1
fi

printf "${YELLOW}[1/4] Tearing down stack (docker compose down)...${RESET}\n"
if docker compose down; then
    printf "${GREEN}  [PASS] docker compose down completed${RESET}\n"
else
    printf "${RED}  [FAIL] docker compose down exited $?${RESET}\n"
    exit 1
fi

if [[ $TEARDOWN -eq 1 ]]; then
    printf "\n${CYAN}Stack is down. --down set; exiting.${RESET}\n"
    exit 0
fi

if [[ $NO_BUILD -eq 0 ]]; then
    printf "\n${YELLOW}[2/4] Rebuilding service images (docker compose build)...${RESET}\n"
    # Only services with a `build:` directive are rebuilt: ingestion, core-backend,
    # serverless-engine. The emulator images (postgres, servicebus, sqledge,
    # floci-az) are pulled on first up and cached; this script does not pull.
    if docker compose build; then
        printf "${GREEN}  [PASS] docker compose build completed${RESET}\n"
    else
        printf "${RED}  [FAIL] docker compose build exited $?${RESET}\n"
        exit 1
    fi
else
    printf "\n${YELLOW}[2/4] --no-build set; skipping image rebuild.${RESET}\n"
fi

printf "\n${YELLOW}[3/4] Bringing stack up (docker compose up -d --wait)...${RESET}\n"
if docker compose up -d --wait; then
    printf "${GREEN}  [PASS] docker compose up -d --wait completed${RESET}\n"
else
    printf "${RED}  [FAIL] docker compose up -d --wait exited $?${RESET}\n"
    exit 1
fi

if [[ $NO_VERIFY -eq 1 ]]; then
    printf "\n${CYAN}Stack is up. --no-verify set; skipping verify-emulators.${RESET}\n"
    exit 0
fi

printf "\n${YELLOW}[4/4] Running verify-emulators.sh...${RESET}\n"
exec "$SCRIPT_DIR/verify-emulators.sh"
