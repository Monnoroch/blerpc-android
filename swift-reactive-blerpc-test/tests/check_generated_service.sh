#!/usr/bin/env bash

echo "Running $0 $*..."

export TERM=dumb
/usr/local/bin/protoc \
  --plugin=protoc-gen-rx='generated/swift-reactive-blerpc' \
  --proto_path='.' \
  -I 'swift-reactive-blerpc-test/build/extracted-include-protos/debug/' \
  --rx_out='swift-reactive-blerpc-test/tests' \
  'swift-reactive-blerpc-test/src/main/proto/test_service.proto'

generated_service=swift-reactive-blerpc-test/tests/com/my/device/proto/TestService.swift
expected_service=swift-reactive-blerpc-test/tests/outputs/test_service_expected_output
difflines=$(diff "${generated_service}" "${expected_service}")
if [ $? -ne 0 ]; then
  echo -e "${red_color}Generated proto service is not equals to expected service.${default_color}\n"
  echo "$difflines"
  exit 1
fi

generated_object_file=swift-reactive-blerpc-test/tests/com/my/device/proto/GetValueResponseExtension.swift
expected_object_file=swift-reactive-blerpc-test/tests/outputs/test_nested_object_expected_output
difflines=$(diff "${generated_object_file}" "${expected_object_file}")
if [ $? -ne 0 ]; then
echo -e "${red_color}Generated proto file with object is not equals to expected file.${default_color}\n"
echo "$difflines"
exit 1
fi

generated_enum_file=swift-reactive-blerpc-test/tests/com/my/device/proto/SetValueResponseExtension.swift
expected_enum_file=swift-reactive-blerpc-test/tests/outputs/test_enum_expected_output
difflines=$(diff "${generated_enum_file}" "${expected_enum_file}")
if [ $? -ne 0 ]; then
echo -e "${red_color}Generated proto file with enum is not equals to expected file.${default_color}\n"
echo "$difflines"
exit 1
fi
