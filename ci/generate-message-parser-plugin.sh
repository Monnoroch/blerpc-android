#!/usr/bin/env bash

mkdir -p generated
cat ci/exe-from-jar.sh message-parser/build/libs/message-parser-jdk8.jar > generated/message-parser
chmod +x generated/message-parser
