#!/usr/bin/env bash
# Clone the pinned Lawnicons revision, generate its vector drawables and merge
# them into a self-contained resource tree for the Lawnchair build.
#
# Usage: prepare_lawnicons.sh <revision-file> <cache-dir> <output-res-dir>
set -euo pipefail

REVISION_FILE=$1
CACHE_DIR=$2
OUT_DIR=$3

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REVISION=$(tr -d '[:space:]' < "$REVISION_FILE")
SRC_DIR="$CACHE_DIR/source"

mkdir -p "$CACHE_DIR"
if [ ! -d "$SRC_DIR/.git" ]; then
    rm -rf "$SRC_DIR"
    git clone --filter=blob:none --no-checkout \
        https://github.com/LawnchairLauncher/lawnicons.git "$SRC_DIR"
fi

if [ "$(git -C "$SRC_DIR" rev-parse HEAD 2>/dev/null || true)" != "$REVISION" ]; then
    git -C "$SRC_DIR" fetch --depth 1 origin "$REVISION"
    git -C "$SRC_DIR" checkout -q --detach FETCH_HEAD
fi

"$SRC_DIR/gradlew" -p "$SRC_DIR" :svg-processor:run

python3 "$SCRIPT_DIR/merge_lawnicons.py" "$SRC_DIR" "$OUT_DIR"
