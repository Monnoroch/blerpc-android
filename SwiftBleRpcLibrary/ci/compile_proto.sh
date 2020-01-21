 #!/usr/bin/env bash

set -e

echo "Running $0 $*..."

# generate messages
mkdir 'SwiftBleRpcLibrary/generated'
/usr/local/bin/protoc \
--proto_path='.' \
--swift_out='SwiftBleRpcLibrary/generated' \
--swift_opt=Visibility=Public \
-I 'reactive-blerpc-test/build/extracted-include-protos/debug/' \
'reactive-blerpc-test/src/main/proto/test_service.proto'

# generate message extensions
/usr/local/bin/protoc \
--plugin=protoc-gen-rx='generated/swift-reactive-blerpc' \
--proto_path='.' \
-I 'reactive-blerpc-test/build/extracted-include-protos/debug/' \
--rx_out='SwiftBleRpcLibrary/generated' \
'reactive-blerpc-test/src/main/proto/test_service.proto'

# generate service
/usr/local/bin/protoc \
--plugin=protoc-gen-rx='generated/swift-reactive-blerpc' \
--proto_path='.' \
-I 'reactive-blerpc-test/build/extracted-include-protos/debug/' \
--rx_out='SwiftBleRpcLibrary/generated' \
'reactive-blerpc-test/src/main/proto/test_service.proto'
