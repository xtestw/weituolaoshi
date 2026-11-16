#!/bin/sh
set -eu
VERSION=3.9.9
SHA256=7a9cdf674fc1703d6382f5f330b3d110ea1b512b51f1652846d9e4e8a588d766
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CACHE="${HOME}/.cache/weituolaoshi/apache-maven-${VERSION}"
if [ ! -x "$CACHE/bin/mvn" ]; then
  mkdir -p "${HOME}/.cache/weituolaoshi"
  ARCHIVE="${HOME}/.cache/weituolaoshi/apache-maven-${VERSION}-bin.tar.gz"
  curl -fL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${VERSION}/apache-maven-${VERSION}-bin.tar.gz" -o "$ARCHIVE"
  if command -v shasum >/dev/null 2>&1; then
    ACTUAL=$(shasum -a 256 "$ARCHIVE" | cut -d ' ' -f 1)
  else
    ACTUAL=$(sha256sum "$ARCHIVE" | cut -d ' ' -f 1)
  fi
  if [ "$ACTUAL" != "$SHA256" ]; then
    echo 'Maven archive checksum mismatch' >&2
    exit 1
  fi
  tar -xzf "$ARCHIVE" -C "${HOME}/.cache/weituolaoshi"
fi
exec "$CACHE/bin/mvn" -f "$ROOT/server/pom.xml" "$@"
