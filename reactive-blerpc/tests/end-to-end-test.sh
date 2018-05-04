#!/usr/bin/env bash

/usr/local/proto/protoc \
  --plugin=protoc-gen-rx='generated/reactive-blerpc' \
  --proto_path='.' \
  -I 'blerpcproto/src/main/proto/' \
  --rx_out='reactive-blerpc/tests' \
  'reactive-blerpc/tests/battery_service.proto'

generated_service=$(cat reactive-blerpc/tests/com/device/proto/RxBatteryService.java)
expected_service=$(cat reactive-blerpc/tests/RxBatteryService.java)
if  [ "${generated_service}" != "${expected_service}" ]; then
   echo -e "${red_color}Generated proto service is not equals to expected service.${default_color}\n"
   exit 1
fi

generated_factory=$(cat reactive-blerpc/tests/com/blerpc/reactive/BleServiceFactory.java)
expected_factory=$(cat reactive-blerpc/tests/BleServiceFactory.java)
if  [ "${generated_factory}" != "${expected_factory}" ]; then
   echo -e "${red_color}Generated service factory is not equals to expected factory.${default_color}\n"
   exit 1
fi
