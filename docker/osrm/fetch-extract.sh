#!/bin/sh
# Downloads the configured OSM extract into the shared volume, for osrm-data
# to process. Runs in a small, actively-maintained image (not the EOL-Debian
# osrm-backend image) purely so curl/TLS/CA-certs are current; this container
# never touches OSRM itself. POSIX sh only — Alpine's /bin/sh has no bash.
set -eu

REGION_URL="${OSRM_REGION_URL:-https://download.geofabrik.de/africa/ethiopia-latest.osm.pbf}"
DATA_DIR="/data"
PBF_FILE="${DATA_DIR}/region.osm.pbf"

if [ -f "${DATA_DIR}/region.osrm.partition" ]; then
    echo "[osrm-fetch] Graph already built — skipping download."
    exit 0
fi

if [ -f "${PBF_FILE}" ]; then
    echo "[osrm-fetch] ${PBF_FILE} already present — skipping download."
    exit 0
fi

echo "[osrm-fetch] Downloading ${REGION_URL}"
curl -fL --retry 3 --retry-delay 5 -o "${PBF_FILE}" "${REGION_URL}"
echo "[osrm-fetch] Done"
