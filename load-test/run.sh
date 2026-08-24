#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

TARGET_RPS="${GATLING_TARGET_RPS:-100}"
DURATION_MINUTES="${GATLING_DURATION_MINUTES:-5}"
REPORT_TS="$(date +%s)"
REPORT_DIR="$ROOT/load-test/reports/$REPORT_TS"

mkdir -p "$REPORT_DIR"

echo "PayPulse Gatling load test"
echo "  Gateway:          ${PAYPULSE_GATEWAY_URL:-http://localhost:8090}"
echo "  Target RPS:       $TARGET_RPS (plan target: 1000 on large CI runners)"
echo "  Duration:         ${DURATION_MINUTES}m"
echo "  Report directory: $REPORT_DIR"
echo ""
echo "Hardware guidance: 100 RPS ~ 4 CPU / 8 GB RAM; 1000 RPS ~ 16 CPU / 32 GB RAM for gateway + dependencies."

export GATLING_TARGET_RPS="$TARGET_RPS"
export GATLING_DURATION_MINUTES="$DURATION_MINUTES"

./gradlew :load-test:gatlingRun \
  -Dgatling.simulationClass=paypulse.PaymentBurstSimulation \
  -Dgatling.core.directory.results="$REPORT_DIR" \
  --no-daemon

# Gatling plugin writes under build/reports/gatling; copy latest HTML into REPORT_DIR if needed
GATLING_OUT="$ROOT/load-test/build/reports/gatling"
if [[ ! -f "$REPORT_DIR/index.html" && -d "$GATLING_OUT" ]]; then
  LATEST="$(find "$GATLING_OUT" -maxdepth 1 -type d -printf '%T@ %p\n' 2>/dev/null | sort -nr | head -1 | cut -d' ' -f2- || true)"
  if [[ -n "${LATEST:-}" && -f "$LATEST/index.html" ]]; then
    cp -a "$LATEST/." "$REPORT_DIR/"
  fi
fi

echo ""
echo "HTML report: $REPORT_DIR/index.html"
ls -la "$REPORT_DIR/index.html"
