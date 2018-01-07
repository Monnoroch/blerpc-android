#!/bin/bash

echo "Running $0..."

set -e

echo "Install google-java-format"

GOOGLE_JAVA_FORMAT_HOME=linters/bin/google-java-format
mkdir -p "${GOOGLE_JAVA_FORMAT_HOME}" && \
file_path="${GOOGLE_JAVA_FORMAT_HOME}/google-java-format.jar" && \
wget --output-document="${file_path}" --quiet https://github.com/google/google-java-format/releases/download/google-java-format-1.4/google-java-format-1.4-all-deps.jar && \
echo "ffee10177bc7b58aeef61466c4f962ea  ${file_path}" | md5sum -c


echo "Running install/xmllint.sh"

sudo apt-get update
sudo apt-get install -y --no-install-recommends libxml2-utils=2.9.*
sudo apt-get clean
sudo rm -rf /var/lib/apt/lists/*
