#!/usr/bin/env bash

PROTO_HOME=/usr/local/proto
export PATH=$PATH:${PROTO_HOME}
mkdir -p /tmp/work
file_path=/tmp/work/protoc.zip
wget --output-document=${file_path} --quiet \
    https://github.com/google/protobuf/releases/download/v3.0.0-beta-2/protoc-3.0.0-beta-2-linux-x86_64.zip
echo "fbbca2f5c921a35f09aa3256da1e2fcb  ${file_path}" | md5sum -c
mkdir -p "${PROTO_HOME}"
unzip ${file_path} -d "${PROTO_HOME}"
chmod +x "${PROTO_HOME}"/protoc
rm -rf /tmp/work
