#!/usr/bin/env bash
# Offline check that needs only a JDK (no Maven/Spring download). Compiles config/domain/repository/service
# against tiny stub annotations and runs Harness. Optional arg = mutant name: the run MUST then FAIL.
# Mutants: nolock lifo noeligibility nocapacity dropnocheck removeineligible
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$HERE/.."
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/src" "$WORK/out"
cp -r "$ROOT/src/main/java/com" "$WORK/src/"
[ $# -gt 0 ] && python3 "$HERE/mutate.py" "$1" "$WORK/src/com/example/waitlist/service/RegistrationService.java"
cd "$WORK/src"
javac -d "$WORK/out" $(find "$HERE/stubs" -name '*.java') \
  $(find com/example/waitlist/{config,domain,repository,service} -name '*.java') "$HERE/Harness.java"
timeout 120 java -cp "$WORK/out" Harness
