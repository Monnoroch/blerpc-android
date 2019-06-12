#!/usr/bin/env bash

echo "Running $0 $*..."

export TERM=dumb
/usr/local/bin/protoc \
  --plugin=protoc-gen-rx='generated/swift-reactive-blerpc' \
  --proto_path='.' \
  -I 'reactive-blerpc-test/build/extracted-include-protos/debug/' \
  --rx_out='swift-reactive-blerpc-test/tests' \
  'reactive-blerpc-test/src/main/proto/test_service.proto'

generated_service=swift-reactive-blerpc-test/tests/com/device/proto/TestService.swift
expected_service=swift-reactive-blerpc-test/tests/outputs/test_expected_service_output
difflines=$(diff "${generated_service}" "${expected_service}")
if [ $? -ne 0 ]; then
  echo -e "${red_color}Generated proto service is not equals to expected service.${default_color}\n"
  echo "$difflines"
  exit 1
fi
