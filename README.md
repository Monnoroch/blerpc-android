[![Build Status](https://travis-ci.org/Monnoroch/blerpc-android.svg?branch=master)](https://travis-ci.org/Monnoroch/blerpc-android)

# blerpc

## Android support


## iOS support
iOS support consists with two separate modules:

1) Swift message parser - this module parse input proto files and generate extensions to messages. Each extension has methods to encode and decode proto message based on proto options data.
2) Swift PromiseKit service generator - generates actual calling requests over Bluettoth Low Energy and calling encode/decode methods from Swift message parser

If you don't need Bluetoth Low Energy support you can use Swift message parser just to parse your proto messages based on proto options data.

### How to use
Firstly you need to write proto file describing your service:

```
syntax = "proto3";

package my.device;

import "blerpc.proto";

service TestService {
option (com.blerpc.service) = {
uuid: "A0000000-0000-0000-0000-000000000000"
};

rpc ReadValue (GetValueRequest) returns (GetValueResponse) {
option (com.blerpc.characteristic) = {
uuid: "A0000001-0000-0000-0000-000000000000"
type: READ
};
}
}

message GetValueRequest {
}

message GetValueResponse {
option (com.blerpc.message) = {
size_bytes: 4
};
int32 int_value = 1 [(com.blerpc.field) = {
from_byte: 0
to_byte: 4
}];
}
```
You can create as many methods as you want. Each proto file must contains one service. Current version of blerpc supports `Read`, `Write` and `Subscribe` methods. See examples for more information.
Assume that plugins was builded (see Android support documentation about building plugins). Then you need to compile proto file with command:

```
protoc service.proto --swift_out=. --swiftgrpc_out=.
```
This will output swift files describing messages. Then call

```
protoc \
--plugin=protoc-gen-rx='path-to-plugin/message-parser' \
--proto_path='.' \
-I 'dependency-protos/' \
--rx_out='output-folder/' \
'proto-to-parse/service.proto'
```

to generate extensions for that swift files to support parsing based on proto options. You can now use parsing like this:

```
let decodedProto = Device_GetValueResponse.decode(data: data)
let encodedProto = Device_SetValueRequest.encode(proto: request)

```

Then optionally you can generate services to call methods over Bluetooth Low Energy. Run

```
protoc \
--plugin=protoc-gen-rx='path-to-plugin/promisekit-blerpc' \
--proto_path='.' \
-I 'dependency-protos/' \
--rx_out='output-folder/' \
'proto-to-parse/service.proto'
```

And finally you can call your service method:

```
// let bleWorker: BleWorker = BleWorker() declared somewhere and connected to device

bleWorker.readValue(request: GetValueRequest()).map { response in
print(response)
}.catch { error in
print(error)
}
```

### Dependencies
BleWorker
PromiseKit - `pod 'PromiseKit'`
SwiftGRPC - `pod 'SwiftGRPC'` (see instructions how to setup it https://github.com/grpc/grpc-swift)
RxBluetoothKit - `pod 'RxBluetoothKit'`

