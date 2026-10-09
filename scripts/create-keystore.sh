#!/usr/bin/env bash
# Creates the release signing key. Keep keystore/ and keystore.properties backed up and out of git.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home}"
KS="$ROOT/keystore/release.jks"

if [ -f "$KS" ]; then echo "$KS already exists - refusing to overwrite."; exit 1; fi
mkdir -p "$ROOT/keystore"

read -r -s -p "Keystore password (min 6 chars): " PASS; echo
read -r -s -p "Repeat password: " PASS2; echo
[ "$PASS" = "$PASS2" ] || { echo "Passwords differ."; exit 1; }

"$JAVA_HOME/bin/keytool" -genkeypair -v -keystore "$KS" -alias iceberg -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=Iceberg Keyboard, O=Personal, C=US"

cat > "$ROOT/keystore.properties" <<PROPS
storeFile=keystore/release.jks
storePassword=$PASS
keyAlias=iceberg
keyPassword=$PASS
PROPS
chmod 600 "$ROOT/keystore.properties" "$KS"
echo "Created $KS and keystore.properties. BACK THEM UP - updates require the same key."
