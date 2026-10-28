#!/usr/bin/env bash
# scripts/fetch-docs.sh
# Sparse, shallow, pinned clones of each product's docs into data/raw/<product>.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/data/manifest.yaml"
RAW="$ROOT/data/raw"

for tool in git yq; do
  command -v "$tool" >/dev/null || { echo "missing tool: $tool" >&2; exit 1; }
done

mkdir -p "$RAW"
# Safety net: raw checkouts must never be committed.
git -C "$ROOT" check-ignore -q data/raw/ || { echo "data/raw/ must be in .gitignore" >&2; exit 1; }

field() {   # usage: field <product> <yq-expression>
  yq -r ".products[] | select(.name == \"$1\") | $2" "$MANIFEST"
}

for product in $(yq -r '.products[].name' "$MANIFEST"); do
  repo=$(field "$product" '.repo')
  ref=$(field "$product" '.ref')
  commit=$(field "$product" '.commit')
  docs_root=$(field "$product" '.docs_root')
  local_only=$(field "$product" '.local_only')
  dest="$RAW/$product"
  target="${commit:-$ref}"

  if [ "$local_only" = "true" ]; then
    echo "[$product] LOCAL ONLY: never copy its text into committed files"
  fi

  [ -d "$dest/.git" ] || { git init -q "$dest"; git -C "$dest" remote add origin "$repo"; }
  git -C "$dest" sparse-checkout set "$docs_root"
  git -C "$dest" fetch --quiet --depth 1 --filter=blob:none origin "$target"
  git -C "$dest" checkout --quiet FETCH_HEAD

  actual=$(git -C "$dest" rev-parse HEAD)
  if [ -z "$commit" ]; then
    echo "[$product] first run: set commit: \"$actual\" in data/manifest.yaml"
  elif [ "$actual" != "$commit" ]; then
    echo "[$product] SHA MISMATCH manifest=$commit actual=$actual" >&2
    exit 1
  else
    echo "[$product] pinned OK at ${actual:0:12}"
  fi
done
