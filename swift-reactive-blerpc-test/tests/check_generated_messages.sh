#!/usr/bin/env bash

echo "Running $0 $*..."

export TERM=dumb
/usr/local/bin/protoc \
  --plugin=protoc-gen-rx='generated/swift-reactive-blerpc' \
  --proto_path='.' \
  -I 'reactive-blerpc-test/build/extracted-include-protos/debug/' \
  --rx_out='swift-reactive-blerpc-test/tests' \
  'reactive-blerpc-test/src/main/proto/test_service.proto'

generated_request_file=swift-reactive-blerpc-test/tests/com/device/proto/GetValueRequestExtension.swift
expected_request_file=swift-reactive-blerpc-test/tests/outputs/test_expected_request_output
difflines=$(diff "${generated_request_file}" "${expected_request_file}")
if [ $? -ne 0 ]; then
echo -e "${red_color}Generated proto file with object is not equals to expected file.${default_color}\n"
echo "$difflines"
exit 1
fi

generated_response_file=swift-reactive-blerpc-test/tests/com/device/proto/GetValueResponseExtension.swift
expected_response_file=swift-reactive-blerpc-test/tests/outputs/test_expected_response_output
difflines=$(diff "${generated_response_file}" "${expected_response_file}")
if [ $? -ne 0 ]; then
echo -e "${red_color}Generated proto file with enum is not equals to expected file.${default_color}\n"
echo "$difflines"
exit 1
fi

generated_nested_request_file=swift-reactive-blerpc-test/tests/com/device/proto/SetValueRequestExtension.swift
expected_nested_request_file=swift-reactive-blerpc-test/tests/outputs/test_expected_set_request_output
difflines=$(diff "${generated_nested_request_file}" "${expected_nested_request_file}")
if [ $? -ne 0 ]; then
echo -e "${red_color}Generated proto file with enum is not equals to expected file.${default_color}\n"
echo "$difflines"
exit 1
fi
