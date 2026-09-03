#!/usr/bin/env bash
# Builds the MLD routing graph from the extract osrm-fetch already downloaded
# into the shared volume. Runs once; the `osrm` service then serves /data
# from the same volume with no reprocessing. Safe to re-run: skips work if a
# graph already exists, so `docker compose up` stays fast after the first run.
set -euo pipefail

PROFILE="${OSRM_PROFILE:-car}"
DATA_DIR="/data"
PBF_FILE="${DATA_DIR}/region.osm.pbf"
OSRM_FILE="${DATA_DIR}/region.osrm"

if [ -f "${OSRM_FILE}.partition" ]; then
    echo "[osrm-data] Graph already present at ${OSRM_FILE} — skipping."
    echo "[osrm-data] Delete the 'osrm_data' Docker volume to force a rebuild (e.g. after a fresher extract)."
    exit 0
fi

if [ ! -f "${PBF_FILE}" ]; then
    echo "[osrm-data] ${PBF_FILE} not found — osrm-fetch must complete first." >&2
    exit 1
fi

echo "[osrm-data] Extracting (profile: ${PROFILE})"
osrm-extract -p "/opt/${PROFILE}.lua" "${PBF_FILE}"

echo "[osrm-data] Partitioning (MLD)"
osrm-partition "${OSRM_FILE}"

echo "[osrm-data] Customizing (MLD)"
osrm-customize "${OSRM_FILE}"

rm -f "${PBF_FILE}"
echo "[osrm-data] Done — graph ready at ${OSRM_FILE}"
