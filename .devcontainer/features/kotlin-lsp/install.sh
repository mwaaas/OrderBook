#!/bin/bash
set -e

VERSION=${VERSION:-"261.13587.0"}
VSIX_FILE="kotlin-lsp-${VERSION}-linux-x64.vsix"
DOWNLOAD_PATH="/tmp/kotlin-lsp.vsix"

echo "Downloading Kotlin Language Server ${VERSION}..."
curl -L "https://download-cdn.jetbrains.com/kotlin-lsp/${VERSION}/${VSIX_FILE}" -o "${DOWNLOAD_PATH}"
echo "Kotlin Language Server downloaded to ${DOWNLOAD_PATH}"