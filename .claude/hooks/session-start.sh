#!/bin/bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

if [ -S /var/run/docker.sock ] && docker info >/dev/null 2>&1; then
  exit 0
fi

nohup dockerd >/var/log/dockerd.log 2>&1 &

for _ in $(seq 1 30); do
  if [ -S /var/run/docker.sock ] && docker info >/dev/null 2>&1; then
    exit 0
  fi
  sleep 1
done

echo "dockerd did not become ready within 30s" >&2
exit 1
