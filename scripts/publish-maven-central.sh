#!/usr/bin/env bash
#
# Publish the Koard Android SDK to Maven Central (Central Portal) under
# groupId `com.koard`, from the prebuilt AAR in libs-maven/. No SDK rebuild.
#
# Central requires, for every artifact: a POM with name/description/url/
# licenses/developers/scm, a sources jar, a javadoc jar, and a GPG signature
# (.asc) + checksums for each file. This script assembles all of that into a
# bundle zip and uploads it to the Central Portal.
#
# Prerequisites (one-time, done by you):
#   1. Sonatype Central account (https://central.sonatype.com, sign in w/ GitHub).
#   2. Register + DNS-verify the `com.koard` namespace (TXT record on koard.com).
#   3. Generate a Central publishing token (User Token) → user + password.
#   4. A GPG key whose PUBLIC key is uploaded to a keyserver
#      (e.g. `gpg --keyserver keys.openpgp.org --send-keys <KEYID>`).
#
# Required env:
#   CENTRAL_USER      Central token username
#   CENTRAL_PASSWORD  Central token password
#   GPG_KEY_ID        GPG key id/email to sign with (e.g. DAC5DAB9C1BBA461)
# Optional env:
#   GPG_PASSPHRASE    passphrase for the key (if it has one)
#   PUBLISHING_TYPE   USER_MANAGED (default; you release from the portal UI)
#                     or AUTOMATIC (release immediately after validation)
#
# Usage: scripts/publish-maven-central.sh [version]     # default 1.0.6
set -euo pipefail

GROUP="com.koard"
ARTIFACT="koard-android-sdk"
VERSION="${1:-1.0.6}"
PUBLISHING_TYPE="${PUBLISHING_TYPE:-USER_MANAGED}"

: "${CENTRAL_USER:?set CENTRAL_USER to your Central token username}"
: "${CENTRAL_PASSWORD:?set CENTRAL_PASSWORD to your Central token password}"
: "${GPG_KEY_ID:?set GPG_KEY_ID to the signing key id}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_AAR="$ROOT/libs-maven/com/koardlabs/$ARTIFACT/$VERSION/$ARTIFACT-$VERSION.aar"
[[ -f "$SRC_AAR" ]] || { echo "✗ AAR not found: $SRC_AAR" >&2; exit 1; }

JAR="${JAR:-/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/jar}"
[[ -x "$JAR" ]] || JAR="$(command -v jar)"

WORK="$(mktemp -d)"
GPATH="${GROUP//.//}/$ARTIFACT/$VERSION"
DEST="$WORK/$GPATH"
mkdir -p "$DEST"
BASE="$ARTIFACT-$VERSION"

echo "▶ assembling bundle for $GROUP:$ARTIFACT:$VERSION"

# --- main artifact ---
cp "$SRC_AAR" "$DEST/$BASE.aar"

# --- POM (Central-required metadata) ---
cat > "$DEST/$BASE.pom" <<POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>$GROUP</groupId>
  <artifactId>$ARTIFACT</artifactId>
  <version>$VERSION</version>
  <packaging>aar</packaging>

  <name>Koard Android SDK</name>
  <description>Koard Merchant SDK for Android — Tap to Pay and merchant transaction processing.</description>
  <url>https://www.koard.com</url>

  <licenses>
    <license>
      <name>Koard SDK License Agreement</name>
      <url>https://github.com/koardlabs/koard-android/blob/main/LICENSE</url>
      <distribution>repo</distribution>
    </license>
  </licenses>

  <developers>
    <developer>
      <id>koardlabs</id>
      <name>Koard Labs</name>
      <email>support@koard.com</email>
      <organization>Koard</organization>
      <organizationUrl>https://www.koard.com</organizationUrl>
    </developer>
  </developers>

  <scm>
    <connection>scm:git:https://github.com/koardlabs/koard-android.git</connection>
    <developerConnection>scm:git:ssh://git@github.com/koardlabs/koard-android.git</developerConnection>
    <url>https://github.com/koardlabs/koard-android</url>
  </scm>

  <dependencies>
    <dependency><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-parcelize-runtime</artifactId><version>2.2.21</version><scope>runtime</scope></dependency>
    <dependency><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-stdlib</artifactId><version>2.2.21</version><scope>runtime</scope></dependency>
    <dependency><groupId>androidx.security</groupId><artifactId>security-crypto</artifactId><version>1.1.0</version><scope>runtime</scope></dependency>
    <dependency><groupId>com.google.android.gms</groupId><artifactId>play-services-safetynet</artifactId><version>18.0.1</version><scope>runtime</scope></dependency>
    <dependency><groupId>com.nimbusds</groupId><artifactId>nimbus-jose-jwt</artifactId><version>10.0.2</version><scope>runtime</scope></dependency>
  </dependencies>
</project>
POM

# --- stub sources + javadoc jars (Central requires both; SDK is closed-source) ---
STUB="$WORK/stub"; mkdir -p "$STUB/META-INF"
printf 'Koard Android SDK %s\nSee https://www.koard.com for documentation.\n' "$VERSION" > "$STUB/README.txt"
( cd "$STUB" && "$JAR" -cf "$DEST/$BASE-sources.jar" README.txt )
( cd "$STUB" && "$JAR" -cf "$DEST/$BASE-javadoc.jar" README.txt )

# --- sign + checksum every artifact ---
GPG_ARGS=(--batch --yes --local-user "$GPG_KEY_ID" --armor --detach-sign)
[[ -n "${GPG_PASSPHRASE:-}" ]] && GPG_ARGS=(--pinentry-mode loopback --passphrase "$GPG_PASSPHRASE" "${GPG_ARGS[@]}")
for f in "$DEST/$BASE".aar "$DEST/$BASE".pom "$DEST/$BASE"-sources.jar "$DEST/$BASE"-javadoc.jar; do
  gpg "${GPG_ARGS[@]}" "$f"
  md5 -q "$f" > "$f.md5"
  shasum -a 1 "$f" | awk '{print $1}' > "$f.sha1"
done

# --- bundle zip (maven layout at the root) ---
BUNDLE="$WORK/central-bundle-$ARTIFACT-$VERSION.zip"
( cd "$WORK" && zip -qr "$BUNDLE" "${GROUP%%.*}" )
echo "▶ bundle: $BUNDLE"

# --- upload to Central Portal ---
AUTH="$(printf '%s:%s' "$CENTRAL_USER" "$CENTRAL_PASSWORD" | base64)"
echo "▶ uploading to Central Portal (publishingType=$PUBLISHING_TYPE)…"
DEPLOY_ID="$(curl -sS -f \
  -H "Authorization: Bearer $AUTH" \
  -F "bundle=@$BUNDLE" \
  "https://central.sonatype.com/api/v1/publisher/upload?name=$ARTIFACT-$VERSION&publishingType=$PUBLISHING_TYPE")"
echo "✓ uploaded. deployment id: $DEPLOY_ID"
echo "  Track/release it at https://central.sonatype.com/publishing/deployments"
[[ "$PUBLISHING_TYPE" == "USER_MANAGED" ]] && echo "  (USER_MANAGED: review + click Publish in the portal to release to Central.)"
