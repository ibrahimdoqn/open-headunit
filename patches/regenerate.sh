#!/usr/bin/env bash
# Rewrites the .patch files from the commits that sit on top of upstream.
# Usage (from the repository root):  ./patches/regenerate.sh [upstream-ref]
# upstream-ref defaults to upstream/main (add it once with:
#   git remote add upstream https://github.com/andreknieriem/open-headunit.git)
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
BASE="${1:-upstream/main}"
TMP="$(mktemp -d)"
# Commits that only touch patches/ are left out of the patch set.
git format-patch "$BASE"..HEAD --no-signature -o "$TMP" -- . ':(exclude)patches' >/dev/null
rm -f patches/*.patch
n=0
for p in "$TMP"/*.patch; do
  [ -s "$p" ] || continue
  n=$((n+1))
  cp "$p" "patches/$(basename "$p")"
done
rm -rf "$TMP"
echo "$n patch(es) written to patches/:"
ls patches/*.patch
