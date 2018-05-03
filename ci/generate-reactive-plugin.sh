#!/usr/bin/env bash

mkdir -p generated
cat ci/exe-from-jar.sh reactive-blerpc/build/libs/reactive-blerpc.jar > generated/reactive-blerpc
chmod +x generated/reactive-blerpc
