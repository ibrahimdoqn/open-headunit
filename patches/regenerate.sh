#!/usr/bin/env bash
# Rewrites patches/*.patch as ONE patch: everything this branch changes against upstream,
# outside patches/, with patches/commit-message.txt as its message.
# Usage (from the repository root):  ./patches/regenerate.sh [upstream-ref]
# upstream-ref defaults to upstream/main (add it once with:
#   git remote add upstream https://github.com/andreknieriem/open-headunit.git)
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
BASE="${1:-upstream/main}"
BASE_SHA="$(git merge-base "$BASE" HEAD)"
MSG="patches/commit-message.txt"
[ -f "$MSG" ] || { echo "missing $MSG"; exit 1; }
WT="$(mktemp -d)"
OUT="$(mktemp -d)"
trap 'git worktree remove --force "$WT" >/dev/null 2>&1 || true; rm -rf "$OUT"' EXIT
git worktree add -q --detach "$WT" "$BASE_SHA"
git diff --binary "$BASE_SHA" HEAD -- . ':(exclude)patches' | git -C "$WT" apply --index
git -C "$WT" commit -q -F "$PWD/$MSG"
git -C "$WT" format-patch -1 HEAD --no-signature -o "$OUT" >/dev/null
rm -f patches/*.patch
cp "$OUT"/*.patch patches/
echo "Wrote against $(git log -1 --format='%h %s' "$BASE_SHA"):"
ls patches/*.patch
