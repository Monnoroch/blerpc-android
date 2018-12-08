#!/usr/bin/env bash

echo "Running $0 $*..."

export TERM=dumb
/tmp/proto/protoc \
  --plugin=protoc-gen-rx='generated/reactive-blerpc' \
  --proto_path='.' \
  -I 'reactive-blerpc-test/build/extracted-include-protos/debug/' \
  --rx_out='reactive-blerpc-test/tests' \
  'reactive-blerpc-test/tests/protos/test_service_no_javadoc.proto'

generated_service=reactive-blerpc-test/tests/com/device/proto/RxTestService.java
expected_service=reactive-blerpc-test/tests/outputs/test_service_no_javadoc_expected_output
difflines=$(diff "${generated_service}" "${expected_service}")
if [ $? -ne 0 ]; then
  echo -e "${red_color}Generated proto service is not equals to expected service.${default_color}\n"
  echo "$difflines"
  exit 1
fi

generated_factory=reactive-blerpc-test/tests/com/blerpc/reactive/BleServiceFactory.java
expected_factory=reactive-blerpc-test/tests/outputs/test_factory_expected_output
difflines=$(diff "${generated_factory}" "${expected_factory}")
if [ $? -ne 0 ]; then
  echo -e "${red_color}Generated service factory is not equals to expected factory.${default_color}\n"
  echo "$difflines"
  exit 1
fi
