#!/usr/bin/env bash

mkdir -p generated
cat ci/exe-from-jar.sh promisekit-blerpc/build/libs/promisekit-blerpc-jdk8.jar > generated/promisekit-blerpc
chmod +x generated/promisekit-blerpc
