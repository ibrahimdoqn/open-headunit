#!/usr/bin/env bash
# Applies every patch in this folder, in order, on top of the current checkout.
# Usage (from the repository root):  ./patches/apply.sh
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
DIR="patches"
# Copy the patches out first: checking out a fresh upstream may remove this folder.
TMP="$(mktemp -d)"
cp "$DIR"/*.patch "$TMP"/
# "git checkout <branch> -- patches" stages the folder, and git am refuses a dirty index.
git reset -q -- "$DIR" 2>/dev/null || true
for p in "$TMP"/*.patch; do
  echo ">> $(basename "$p")"
  if ! git am -3 "$p"; then
    echo "!! $(basename "$p") did not apply cleanly."
    echo "   Fix the conflicts, then: git add <files> && git am --continue"
    echo "   (or give up: git am --abort)"
    exit 1
  fi
done
rm -rf "$TMP"
echo "All patches applied."
