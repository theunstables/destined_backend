#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DESTINATION="${1:-$PWD}"
ARCHIVE="$SCRIPT_DIR/ziyu_backend_java_source.zip"
FIRST_PDF="$SCRIPT_DIR/ziyu_backend_java_part01.pdf"
EXPECTED_SHA256="$(grep -a '^%LOVEOS-SHA256:' "$FIRST_PDF" | head -n 1 | sed 's/^%LOVEOS-SHA256://')"
cat "$SCRIPT_DIR"/ziyu_backend_java_part*.pdf | grep -a '^%LOVEOS-PAYLOAD:' | sed 's/^%LOVEOS-PAYLOAD://' | tr -d '\r\n' | base64 --decode > "$ARCHIVE"
if command -v sha256sum >/dev/null 2>&1; then
  ACTUAL_SHA256="$(sha256sum "$ARCHIVE" | awk '{print $1}')"
else
  ACTUAL_SHA256="$(shasum -a 256 "$ARCHIVE" | awk '{print $1}')"
fi
if [[ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]]; then
  echo "Checksum mismatch; a PDF is missing or damaged." >&2
  rm -f "$ARCHIVE"
  exit 1
fi
mkdir -p "$DESTINATION"
unzip -q "$ARCHIVE" -d "$DESTINATION"
rm -f "$ARCHIVE"
echo "Restored: $DESTINATION/ziyu_backend_java"
