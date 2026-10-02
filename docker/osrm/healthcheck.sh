#!/usr/bin/env bash
# Docker healthcheck for osrm-routed. OSRM has no native /health route, and
# the base image (Debian 9) has no curl/wget, so this speaks raw HTTP/1.1
# over bash's /dev/tcp against a real, in-bounds request (Addis Ababa) and
# checks for a genuine successful OSRM response — not just "port is open".
set -euo pipefail

exec 3<>/dev/tcp/127.0.0.1/5000
printf 'GET /nearest/v1/driving/38.7469,9.0320 HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3

response=$(cat <&3)
exec 3<&- 3>&-

echo "${response}" | grep -q '"code":"Ok"'
