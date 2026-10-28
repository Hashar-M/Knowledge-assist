#!/usr/bin/env bash
# scripts/select-starter.sh - copy the files listed in data/starter-files.txt
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/data/manifest.yaml"
RAW="$ROOT/data/raw"
OUT="$ROOT/data/starter"
LIST="$ROOT/data/starter-files.txt"

rm -rf "$OUT"
while IFS= read -r line; do
  line="${line%%#*}"                      # drop trailing comments
  line="$(echo "$line" | xargs)"          # trim spaces
  [ -z "$line" ] && continue

  product="${line%%/*}"
  page="${line#*/}"
  docs_root=$(yq -r ".products[] | select(.name == \"$product\") | .docs_root" "$MANIFEST")
  src="$RAW/$product/$docs_root/$page"

  [ -f "$src" ] || { echo "not found: $src (fix the path or fetch first)" >&2; exit 1; }
  mkdir -p "$OUT/$product/$(dirname "$page")"
  cp "$src" "$OUT/$product/$page"
done < "$LIST"

echo "starter files copied: $(find "$OUT" -type f | wc -l)"
