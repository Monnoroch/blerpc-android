#!/usr/bin/env bash

PROTO_HOME=/tmp/proto
mkdir -p /tmp/work
file_path=/tmp/work/protoc.zip
wget --output-document=${file_path} --quiet \
    https://github.com/protocolbuffers/protobuf/releases/download/v3.6.1/protoc-3.6.1-linux-x86_64.zip
echo "a18f0c38ceb3c71c3b7db5994d4fd4fc  ${file_path}" | md5sum -c
mkdir -p "${PROTO_HOME}"
unzip ${file_path} -d "${PROTO_HOME}"
mv "${PROTO_HOME}"/bin/protoc "${PROTO_HOME}"/protoc
chmod +x "${PROTO_HOME}"/protoc && \
rm -rf /tmp/work
