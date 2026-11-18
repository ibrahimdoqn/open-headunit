#!/usr/bin/env bash
# Rewrites patches/*.patch: one patch per commit on top of upstream, leaving out commits that
# only touch patches/. Also records the upstream commit they apply to in patches/BASE.
# Usage (from the repository root):  ./patches/regenerate.sh [upstream-ref]
# upstream-ref defaults to upstream/main (add it once with:
#   git remote add upstream https://github.com/andreknieriem/open-headunit.git)
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
BASE="$(git merge-base "${1:-upstream/main}" HEAD)"
OUT="$(mktemp -d)"
git format-patch "$BASE"..HEAD --no-signature -o "$OUT" -- . ':(exclude)patches' >/dev/null
rm -f patches/*.patch
cp "$OUT"/*.patch patches/ 2>/dev/null || true
rm -rf "$OUT"
echo "$BASE" > patches/BASE
echo "Patches against $(git log -1 --format='%h %s' "$BASE"):"
ls patches/*.patch
