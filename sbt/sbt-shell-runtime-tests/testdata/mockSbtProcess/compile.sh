#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$SCRIPT_DIR/src"
OUT_DIR="$SCRIPT_DIR/classes"
SOURCE_FILE="$SRC_DIR/MockSbtProcess.java"

mkdir -p "$OUT_DIR"
javac --release 8 -d "$OUT_DIR" "$SOURCE_FILE"
