[![Build Status](https://travis-ci.org/Monnoroch/blerpc-android.svg?branch=master)](https://travis-ci.org/Monnoroch/blerpc-android)

# blerpc

## Android support


## iOS support
Swift PromiseKit service generator plugin generates PromiseKit wrappers to request methods over Bluettoth Low Energy and automatically encode/decode messages.

### How to use
You need to add classes from `SwiftBleModule` to your project. These classes is high level classes ovr Bluetooth Low Energy interaction and parsing.

Firstly you need to write proto file describing your service:

```
syntax = "proto3";

package my.device;

import "blerpc.proto";

// A service for testing blerpc.
service TestService {
    option (com.blerpc.service) = {
        uuid: "A0000000-0000-0000-0000-000000000000"
    };

    // Read value test method.
    rpc ReadValue (GetValueRequest) returns (GetValueResponse) {
        option (com.blerpc.characteristic) = {
            uuid: "A0000001-0000-0000-0000-000000000000"
            type: READ
        };
    }

    // Write value test method.
    rpc WriteValue (SetValueRequest) returns (SetValueResponse) {
        option (com.blerpc.characteristic) = {
            uuid: "A0000001-0000-0000-0000-000000000000"
            type: WRITE
        };
    }

    // Subscribe for receiving test value updates.
    rpc GetValueUpdates (GetValueRequest) returns (stream GetValueResponse) {
        option (com.blerpc.characteristic) = {
            uuid: "A0000001-0000-0000-0000-000000000000"
            descriptor_uuid: "00000000-0000-0000-0000-000000000000"
            type: SUBSCRIBE
        };
    }
}

// Request message for the ReadValue and GetValueUpdates methods.
message GetValueRequest {
}

// Response message for the ReadValue and GetValueUpdates methods.
message GetValueResponse {
    option (com.blerpc.message) = {
        size_bytes: 4
    };
    // Integer value.
    int32 int_value = 1 [(com.blerpc.field) = {
        from_byte: 0
        to_byte: 4
    }];
}

// Request message for the WriteValue method.
message SetValueRequest {
}

// Response message for the WriteValue.
message SetValueResponse {
    option (com.blerpc.message) = {
        size_bytes: 4
    };
    // Integer value.
    int32 int_value = 1 [(com.blerpc.field) = {
        from_byte: 0
        to_byte: 4
    }];
}
```
You can create as many methods as you want. Each proto file must contains one service. Current version of blerpc supports `Read`, `Write` and `Subscribe` methods.

Let's assume that plugins was builded (see Android support documentation about building plugins). Then you need to compile proto file with command:

```
protoc service.proto --swift_out=. --swiftgrpc_out=.
```
This will output swift files describing messages. Then call

```
protoc \
--plugin=protoc-gen-rx='path-to-plugin/promisekit-blerpc' \
--proto_path='.' \
-I 'dependency-protos/' \
--rx_out='output-folder/' \
'proto-to-parse/service.proto'
```

to generate PromieKit services for calling methods over Bluetooth Low Energy and extensions for Swift files which we already compiled in step above to support  parsing messages.

And finally you can call your service methods:

```
let bleWorker: BleWorker = BleWorker()
let testService: TestService = TestService.init(bleWorker)

// Read method example
testService.readValue(request: GetValueRequest()).map { response in
    print(response)
}.catch { error in
    print(error)
}

// Write method example
testService.writeValue(request: SetValueRequest()).map { response in
    print(response)
}.catch { error in
    print(error)
}

// Subscribe method example
let handler1 = testService.getValueUpdates(request: GetValueRequest(), completion: { response in
    print(response)
}, error: { error in
    print(error)
})

let handler2 = testService.getValueUpdates(request: GetValueRequest(), completion: { response in
    print(response)
}, error: { error in
    print(error)
})

handler1.unsubscribe() // physically not unsubscribed
handler2.unsubscribe() // now physically unsubscribed (because no more handlers)
```

### Dependencies
PromiseKit - `pod 'PromiseKit'`
SwiftGRPC - `pod 'SwiftGRPC'` (see instructions how to setup it https://github.com/grpc/grpc-swift)
RxBluetoothKit - `pod 'RxBluetoothKit'`
