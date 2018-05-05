#!/usr/bin/env bash

/tmp/proto/protoc \
  --plugin=protoc-gen-rx='generated/reactive-blerpc' \
  --proto_path='.' \
  -I 'blerpcproto/src/main/proto/' \
  --rx_out='reactive-blerpc-test/tests' \
  'reactive-blerpc-test/src/main/proto/test_service.proto'

generated_service=reactive-blerpc-test/tests/com/device/proto/RxTestService.java
expected_service=reactive-blerpc-test/tests/test_service_expected_output
if !(diff "${generated_service}" "${expected_service}") ; then
   echo -e "${red_color}Generated proto service is not equals to expected service.${default_color}\n"
   exit 1
fi

generated_factory=reactive-blerpc-test/tests/com/blerpc/reactive/BleServiceFactory.java
expected_factory=reactive-blerpc-test/tests/test_factory_expected_output
if !(diff "${generated_factory}" "${expected_factory}") ; then
   echo -e "${red_color}Generated service factory is not equals to expected factory.${default_color}\n"
   exit 1
fi
