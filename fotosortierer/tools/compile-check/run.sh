#!/bin/bash
# Führt einen Gradle-Lauf des Prüfstands aus und wiederholt ihn bei 429-Antworten von Maven Central.
cd "$(dirname "$0")"
for i in $(seq 1 12); do
  out=$(../../gradlew --project-dir . -Dorg.gradle.internal.repository.max.retries=10 -Dorg.gradle.internal.repository.initial.backoff=4000 "$@" --console=plain 2>&1)
  if echo "$out" | grep -q "status code 429"; then echo "429, Versuch $i …" >&2; sleep 20; continue; fi
  echo "$out" | grep -v "^Picked up JAVA_TOOL_OPTIONS"
  exit 0
done
echo "$out" | tail -30
exit 1
