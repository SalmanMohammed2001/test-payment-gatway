#!/usr/bin/env bash
# Start backend (8080) and frontend (4200) for local Secure Acceptance testing.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

# Load credentials from .env if present, so swapping in a new (working)
# CyberSource sandbox account is a one-file edit — no code changes.
if [[ -f "$ROOT/.env" ]]; then
  echo "==> Loading credentials from .env"
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

if ! command -v java >/dev/null; then echo "Java required"; exit 1; fi
if ! command -v mvn >/dev/null; then echo "Maven required"; exit 1; fi
if ! command -v npm >/dev/null; then echo "npm required"; exit 1; fi

echo "==> Building backend..."
mvn -q -DskipTests package

echo "==> Installing frontend deps (if needed)..."
(cd frontend && npm ci --silent 2>/dev/null || npm install --silent)

cleanup() {
  echo ""
  echo "Stopping..."
  [[ -n "${BACKEND_PID:-}" ]] && kill "$BACKEND_PID" 2>/dev/null || true
  [[ -n "${FRONTEND_PID:-}" ]] && kill "$FRONTEND_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "==> Starting backend on :8080..."
java -jar target/cybersource-payment-0.0.1-SNAPSHOT.jar &
BACKEND_PID=$!

for i in $(seq 1 40); do
  if curl -sf http://localhost:8080/api/v1/secure-acceptance/setup >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo "==> Starting frontend on :4200..."
(cd frontend && npm run dev -- --host 127.0.0.1) &
FRONTEND_PID=$!

echo ""
echo "Ready:"
echo "  Checkout:  http://localhost:4200/checkout"
echo "  Setup URLs: http://localhost:8080/api/v1/secure-acceptance/setup?publicBaseUrl=https://YOUR_NGROK_HOST"
echo ""
echo "Business Center checklist:"
echo "  1. Payment Form → Billing Information → DISABLED"
echo "  2. Customer Response → Transaction Response Page → Hosted By You → YOUR_PUBLIC_URL/api/v1/secure-acceptance/response"
echo "  3. Custom Redirect → http://localhost:4200/checkout/result"
echo "  4. Click PROMOTE PROFILE"
echo ""
wait
