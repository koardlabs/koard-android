#!/usr/bin/env bash
#
# Publish the prebuilt Koard Android SDK to GitHub Packages (Maven registry).
# Publishes the already-built artifacts under libs-maven/ (no SDK rebuild).
#
# Auth: needs a token with `write:packages`. Provide via env:
#   GH_PACKAGES_TOKEN  (PAT or `gh auth token` after `gh auth refresh -s write:packages`)
#   GH_PACKAGES_USER   (defaults to the authenticated gh login)
#
# Usage: scripts/publish-gh-packages.sh [version]   # default 1.0.6
set -euo pipefail
OWNER=koardlabs; REPO=koard-android
GROUP_PATH=com/koardlabs/koard-android-sdk
VERSION="${1:-1.0.6}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/libs-maven/$GROUP_PATH/$VERSION"
BASE="https://maven.pkg.github.com/$OWNER/$REPO/$GROUP_PATH/$VERSION"
USER="${GH_PACKAGES_USER:-$(gh api user -q .login)}"
: "${GH_PACKAGES_TOKEN:?set GH_PACKAGES_TOKEN to a token with write:packages}"

[[ -f "$SRC/koard-android-sdk-$VERSION.aar" ]] || { echo "✗ AAR not found: $SRC" >&2; exit 1; }
for f in "koard-android-sdk-$VERSION.pom" "koard-android-sdk-$VERSION.aar"; do
  echo "▶ PUT $f"
  curl -sS -f -u "$USER:$GH_PACKAGES_TOKEN" -X PUT --upload-file "$SRC/$f" "$BASE/$f" \
    || { echo "✗ upload failed for $f (need write:packages?)" >&2; exit 1; }
done
echo "✓ published com.koardlabs:koard-android-sdk:$VERSION to GitHub Packages ($OWNER/$REPO)"
