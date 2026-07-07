#!/usr/bin/env bash
# Load .env and start the CyberSource payment service.
set -euo pipefail
cd "$(dirname "$0")"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

exec mvn spring-boot:run
