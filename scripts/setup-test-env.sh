#!/usr/bin/env bash
set -euo pipefail

echo "==> Checking dependencies..."

command -v java >/dev/null 2>&1 || { echo "ERROR: Java not found. Install JDK 21."; exit 1; }
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
[ "$JAVA_VERSION" -ge 21 ] || { echo "ERROR: Java 21+ required. Found: $JAVA_VERSION"; exit 1; }

command -v ./gradlew >/dev/null 2>&1 || { echo "ERROR: gradlew not found. Run from the repo root."; exit 1; }

echo "==> Creating test directories..."
mkdir -p /tmp/replay-test/queue
mkdir -p /tmp/replay-test/output
mkdir -p sidecar/src/test/fixtures

echo "==> Copying test env file..."
if [ ! -f integration-tests/.env.test ]; then
    cp integration-tests/.env.test.example integration-tests/.env.test
    echo "    Copied .env.test.example -> .env.test"
else
    echo "    .env.test already exists, skipping."
fi

echo "==> Building project..."
./gradlew assemble --no-daemon -q

echo "==> Smoke test: running unit tests..."
./gradlew test --no-daemon -q

echo ""
echo "==> Test environment ready."
echo ""
echo "    Run unit tests:        ./gradlew test"
echo "    Run integration tests: ./gradlew integrationTest"
echo "    Run all tests:         ./gradlew check"
echo "    Run CI locally:        act  (requires 'act' installed: https://github.com/nektos/act)"
