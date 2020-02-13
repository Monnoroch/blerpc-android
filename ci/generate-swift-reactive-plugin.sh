#!/usr/bin/env bash

mkdir -p generated
cat ci/exe-from-jar.sh swift-reactive-blerpc/build/libs/swift-reactive-blerpc-jdk8.jar > generated/swift-reactive-blerpc
chmod +x generated/swift-reactive-blerpc
