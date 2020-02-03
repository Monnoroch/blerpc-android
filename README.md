[![Build Status](https://travis-ci.org/Monnoroch/blerpc-android.svg?branch=master)](https://travis-ci.org/Monnoroch/blerpc-android)

# blerpc

## Android support


## iOS support
Swift Reactive Ble Rpc service generator plugin generates `Rx.Swift` wrappers to request methods over Bluetooth Low Energy and automatically encode/decode messages.

### How to build SwiftBleRpc project (Mac OS)
1. Install protobuf
```
brew install protobuf
```
2. Install gradlew it's required version is 4.6
```
ci/create-gradle-wrapper-4.6.sh
```
* (command should be run from main directory)
3. Check if gradlew installed version is required
```
sh gradlew -v
```
4. Update google and android libs before start generating java files. Open project in Android studio and wait untill project will complete building. 
5. Generate java files
```
sh gradlew reactive-blerpc:assemble reactive-blerpc:test
```
6. Generate swift files
```
sh gradlew swift-reactive-blerpc:assemble swift-reactive-blerpc:test
```
7. Generate java reactive plugin
```
ci/generate-reactive-plugin.sh
```
8. Generate swift reactive plugin
```
ci/generate-swift-reactive-plugin.sh
```
9. Generate swift protobuf files
```
SwiftBleRpcLibrary/ci/compile_proto.sh
```
10. Change directory and run SwiftBleRpc.xcworkspace
```
cd SwiftBleRpcLibrary
open -a Xcode SwiftBleRpc.xcworkspace
```
11. Install pods if it needs
```
pod install
``` 

### How to use
You need to add classes from `SwiftBleRpcLibrary` to your project. These classes is high level classes over Bluetooth Low Energy interaction and parsing.

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
Save it in the directory folder with your project with name 'service.proto'.

You can create as many methods as you want. Each proto file must contains one service. Current version of blerpc supports `Read`, `Write` and `Subscribe` methods.

For successfull compile you need to add `blerpc.proto` file into the root of the directory where your project is.
```
syntax = "proto3";

option java_package = "com.blerpc.proto";
option java_multiple_files = true;
option optimize_for = CODE_SIZE;
option java_generic_services = true;

// A proto part of the BLE RPC library.
package com.blerpc;

import "google/protobuf/descriptor.proto";

// An interaction type with the characteristic.
// A method can interact with a characteristic in three ways: it can write to it, read from it and
// subscribe to it's updates.
enum MethodType {
  // Unknown method type.
  UNKNOWN = 0;
  // A method with this type is allowed to write to a characteristic.
  WRITE = 1;
  // A method with this type is allowed to read the value of a characteristic.
  READ = 2;
  // A method with this type is allowed to subscribe to a characteristic's updates.
  SUBSCRIBE = 3;
}

// BLE characteristic data.
message BleCharacteristicRule {
  // Characteristic UUID.
  string uuid = 1;
  // Descriptor UUID.
  string descriptor_uuid = 3;
  // The method's interaction type with the characteristic.
  MethodType type = 2;
}

message BleServiceRule {
  // Service UUID.
  string uuid = 1;
}

// A description of a message for automatic converter.
message MessageExtension {
  // Message size in bytes.
  int32 size_bytes = 1;
  // Byte order for the message.
  // Default byte order for the message is BIG_ENDIAN.
  ByteOrder byte_order = 2;
}

// A description of a field for automatic converter.
message FieldExtension {
  // The number of the first byte within a message for a field.
  int32 from_byte = 1;
  // The number of the last byte within a message for a field.
  // This bound is excluded, i.e. {from = 3, to = 5} describes a two-byte field.
  int32 to_byte = 2;
  // Byte order for the field.
  // Default byte order for the field is BIG_ENDIAN.
  ByteOrder byte_order = 3;
}

// Byte order type of message or field converted to bytes.
enum ByteOrder {
  // Default byte order that sets when creating com.blerpc.AnnotationMessageConverter.
  DEFAULT = 0;
  // Big endian byte order. Short number "50" is converted into 2 bytes {0, 50}.
  // This is a default byte order.
  BIG_ENDIAN = 1;
  // Little endian byte order. Short number "50" is converter into 2 bytes {50, 0}".
  LITTLE_ENDIAN = 2;
}

// Allow annotating fields with com.blerpc.field annotation to specify parameters for automatic converter.
extend google.protobuf.FieldOptions {
  FieldExtension field = 82595722;
}

// Allow annotating messages with com.blerpc.message annotation to specify parameters for automatic converter.
extend google.protobuf.MessageOptions {
  MessageExtension message = 82595723;
}

// Add `characteristic` method option for attaching the method to a BLE characteristic.
extend google.protobuf.MethodOptions {
  BleCharacteristicRule characteristic = 82595724;
}

// Add `service` service option for attaching the service to a BLE service.
extend google.protobuf.ServiceOptions {
  BleServiceRule service = 82595725;
}
```

Let's assume that plugins was builded (see Android support documentation about building plugins). Then you need to compile proto file with command:

```
protoc service.proto --swift_out=. --swiftgrpc_out=.
```
For successfull files generation you need to add `swift-reactive-blerpc` file from `generated` directory and put it into the root of the directory where your project is.

This will output swift files describing messages. Then call

```
mkdir '<Your project name>/generated'
protoc \
--plugin=protoc-gen-rx='swift-reactive-blerpc' \
--proto_path='.' \
--rx_out='<Your project name>/generated' \
'service.proto'
```

to generate reactive Rpc services for calling methods over Bluetooth Low Energy and extensions for Swift files which we already compiled in step above to support  parsing messages.

And finally you can call your service methods:

```
let bleWorker: BleWorker = BleWorker.init(peripheral: peripheral)

let testService: TestService = TestService.init(bleWorker)

// You can use as many services as you want. You can even use the same services multiple times
let testService2: TestService = TestService.init(bleWorker)

// Read method example
let handler1 = testService.readValue(request: GetValueRequest()).subscribe(onSuccess: { response in
    print(response)
}, onError: { error in
    print(error)
}

handler1.dispose() // you can cancel read request

// Write method example
let handler2 = testService.writeValue(request: SetValueRequest()).subscribe(onSuccess: { response in
    print(response)
}, onError: { error in
    print(error)
}

handler2.dispose() // you can cancel write request

// Subscribe method example
let handler3 = testService.getValueUpdates(request: GetValueRequest()).subscribe(onNext: { response in
    print(response)
}, onError: { error in
    print(error)
})

let handler4 = testService.getValueUpdates(request: GetValueRequest()).subscribe(onNext: { response in
    print(response)
}, onError: { error in
    print(error)
})

// You can subscribe to the same method on different service
let handler5 = testService2.getValueUpdates(request: GetValueRequest()).subscribe(onNext: { response in
    print(response)
}, onError: { error in
    print(error)
})

handler3.dispose() // physically not unsubscribed
handler5.dispose() // physically not unsubscribed
handler4.dispose() // now physically unsubscribed (because no more handlers)

```

### Dependencies
SwiftGRPC - `pod 'SwiftGRPC'` (see instructions how to setup it https://github.com/grpc/grpc-swift)
RxBluetoothKit - `pod 'RxBluetoothKit'`
RxSwiftExt - `pod 'RxSwiftExt'`
