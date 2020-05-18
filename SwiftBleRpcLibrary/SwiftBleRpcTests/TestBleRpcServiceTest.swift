import XCTest
import Nimble
import Cuckoo
import RxBlocking
import RxSwift
@testable import SwiftBleRpc

/**
*  A service for testing BleRpcService.
*/
// swiftlint:disable all
class TestBleRpcServiceTest: XCTestCase {

    var bleRpcDriverMock: MockBleServiceDriver!
    let characteristicUUID: String = "A0000001-0000-0000-0000-000000000000"
    var service: TestService?
    var dispose: Disposable?

    override func setUp() {
        super.setUp()
        bleRpcDriverMock = MockBleServiceDriver(peripheral: CentralManagerSwizzle.instance.peripheral()!)
        service = TestService.init(bleRpcDriverMock)
    }

    override func tearDown() {
        clearStubs(bleRpcDriverMock)
        dispose?.dispose()
    }

    func testRead() throws {
        let testRequest = Device_GetValueRequest()
        let testResponse = Device_GetValueResponse.with({ (response) in
            response.intValue = 94
        })

        stub(bleRpcDriverMock) { stub in
            when(
                stub.read(
                    request: equal(to: try! Device_GetValueRequest.bleRpcEncode(proto: testRequest)),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID
                )
            )
                .thenReturn(.just(try! Device_GetValueResponse.bleRpcEncode(proto: testResponse)))
        }

        let response = try service?.readValue(request: testRequest)
            .toBlocking()
            .first()

        XCTAssertEqual(response, testResponse)
    }

    func testWrite() throws {
        let testRequest = Device_SetValueRequest.with { request in
            request.intValue = 45
        }
        let testResponse = Device_SetValueResponse()
        stub(bleRpcDriverMock) { stub in
            when(
                stub.write(
                    request: equal(to: try! Device_SetValueRequest.bleRpcEncode(proto: testRequest)),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID
                )
            )
                .thenReturn(.just(try! Device_SetValueResponse.bleRpcEncode(proto: testResponse)))
        }

        let response = try service?.writeValue(request: testRequest)
            .toBlocking()
            .first()
        XCTAssertEqual(response, testResponse)
    }

    func testSubscribe() throws {
        let testRequest = Device_GetValueRequest()
        let testResponse = Device_GetValueResponse.with({ (response) in
            response.intValue = 94
        })
        stub(bleRpcDriverMock) { stub in
            when(
                stub.subscribe(
                    request: equal(to: try! Device_GetValueRequest.bleRpcEncode(proto: testRequest)),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID
                )
            )
                .thenReturn(.just(try! Device_GetValueResponse.bleRpcEncode(proto: testResponse)))
        }
        let response = try service?.getValueUpdates(request: testRequest)
            .toBlocking()
            .first()
        XCTAssertEqual(response, testResponse)
    }

    func testSubscribeMultipleValues() throws {
        let testRequest = Device_GetValueRequest()
        let testResponse = Device_GetValueResponse.with { (response) in
            response.intValue = 94
        }
        let testResponseSecond = Device_GetValueResponse.with { (response) in
            response.intValue = 95
        }
        let responseArray = [testResponse, testResponseSecond]
        stub(bleRpcDriverMock) { stub in
            when(
                stub.subscribe(
                    request: equal(to: try! Device_GetValueRequest.bleRpcEncode(proto: testRequest)),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID
                )
            )
                .thenReturn(
                    Observable.from(
                        [
                            try! Device_GetValueResponse.bleRpcEncode(proto: testResponse),
                            try! Device_GetValueResponse.bleRpcEncode(proto: testResponseSecond)
                        ]
                    )
            )
        }
        let response = try service?.getValueUpdates(request: testRequest)
            .toBlocking()
            .toArray()
        XCTAssertEqual(response, responseArray)
    }

    func testReadLongData() throws {
        let testRequest = Device_GetValueRequest()
        let testResponse = Device_GetValueResponse.with({ (response) in
            response.intValue = 94
        })
        var dataResponse = try! Device_GetValueResponse.bleRpcEncode(proto: testResponse)
        dataResponse.append(Data(hex: "0000"))
        stub(bleRpcDriverMock) { stub in
            when(
                stub.read(
                    request: equal(to: try! Device_GetValueRequest.bleRpcEncode(proto: testRequest)),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID
                )
            ).thenReturn(.just(dataResponse))
        }

        let response = try service?.readValue(request: testRequest)
            .toBlocking()
            .first()

        XCTAssertEqual(response, testResponse)
    }

    func testWriteLongData() throws {
        let testRequest = Device_SetValueRequest.with { request in
            request.intValue = 45
        }
        let testResponse = Device_SetValueResponse()
        var dataResponse = try! Device_SetValueResponse.bleRpcEncode(proto: testResponse)
        dataResponse.append(Data(hex: "0000"))
        stub(bleRpcDriverMock) { stub in
            when(
                stub.write(
                    request: equal(to: try! Device_SetValueRequest.bleRpcEncode(proto: testRequest)),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID
                )
            ).thenReturn(.just(dataResponse))
        }

        let response = try service?.writeValue(request: testRequest)
            .toBlocking()
            .first()
        XCTAssertEqual(response, testResponse)
    }

    func testSubscribeLongData() throws {
        let testRequest = Device_GetValueRequest()
        let testResponse = Device_GetValueResponse.with({ (response) in
            response.intValue = 94
        })
        var dataResponse = try! Device_GetValueResponse.bleRpcEncode(proto: testResponse)
        dataResponse.append(Data(hex: "0000"))
        stub(bleRpcDriverMock) { stub in
            when(
                stub.subscribe(
                    request: equal(to: try! Device_GetValueRequest.bleRpcEncode(proto: testRequest)),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID
                )
            ).thenReturn(.just(dataResponse))
        }
        let response = try service?.getValueUpdates(request: testRequest)
            .toBlocking()
            .first()
        XCTAssertEqual(response, testResponse)
    }

    func testSubscribeMultipleValuesLongData() throws {
        let testRequest = Device_GetValueRequest()
        let testResponse = Device_GetValueResponse.with { (response) in
            response.intValue = 94
        }
        let testResponseSecond = Device_GetValueResponse.with { (response) in
            response.intValue = 95
        }
        var dataResponse = try! Device_GetValueResponse.bleRpcEncode(proto: testResponse)
        dataResponse.append(Data(hex: "0000"))
        var dataResponseSecond = try! Device_GetValueResponse.bleRpcEncode(proto: testResponseSecond)
        dataResponseSecond.append(Data(hex: "0000"))
        let responseArray = [testResponse, testResponseSecond]
        stub(bleRpcDriverMock) { stub in
            when(
                stub.subscribe(
                    request: equal(to: try! Device_GetValueRequest.bleRpcEncode(proto: testRequest)),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID
                )
            ).thenReturn(Observable.from([dataResponse, dataResponseSecond]))
        }
        let response = try service?.getValueUpdates(request: testRequest)
            .toBlocking()
            .toArray()
        XCTAssertEqual(response, responseArray)
    }

    func testReadShortData() throws {
        let testRequest = Device_GetValueRequest()
        let testResponse = Device_GetValueResponse.with({ (response) in
            response.intValue = 94
        })
        var dataResponse = try! Device_GetValueResponse.bleRpcEncode(proto: testResponse)
        dataResponse.removeSubrange(2...dataResponse.count - 1)
        stub(bleRpcDriverMock) { stub in
            when(
                stub.read(
                    request: equal(to: try! Device_GetValueRequest.bleRpcEncode(proto: testRequest)),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID
                )
            ).thenReturn(.just(dataResponse))
        }

        let response = try service?.readValue(request: testRequest)
            .toBlocking()
            .first()

        XCTAssertEqual(try! response!.serializedData(), Data())
    }

    func testSubscribeShortData() throws {
        let testRequest = Device_GetValueRequest()
        let testResponse = Device_GetValueResponse.with({ (response) in
            response.intValue = 94
        })
        var dataResponse = try! Device_GetValueResponse.bleRpcEncode(proto: testResponse)
        dataResponse.removeSubrange(2...dataResponse.count - 1)
        stub(bleRpcDriverMock) { stub in
            when(
                stub.subscribe(
                    request: equal(to: try! Device_GetValueRequest.bleRpcEncode(proto: testRequest)),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID
                )
            ).thenReturn(.just(dataResponse))
        }
        let response = try service?.getValueUpdates(request: testRequest)
            .toBlocking()
            .first()
        XCTAssertEqual(try! response!.serializedData(), Data())
    }

    func testSubscribeMultipleValuesShortData() throws {
        let testRequest = Device_GetValueRequest()
        let testResponse = Device_GetValueResponse.with { (response) in
            response.intValue = 94
        }
        let testResponseSecond = Device_GetValueResponse.with { (response) in
            response.intValue = 95
        }
        var dataResponse = try! Device_GetValueResponse.bleRpcEncode(proto: testResponse)
        dataResponse.removeSubrange(2...dataResponse.count - 1)
        var dataResponseSecond = try! Device_GetValueResponse.bleRpcEncode(proto: testResponseSecond)
        dataResponseSecond.removeSubrange(2...dataResponseSecond.count - 1)
        stub(bleRpcDriverMock) { stub in
            when(
                stub.subscribe(
                    request: equal(to: try! Device_GetValueRequest.bleRpcEncode(proto: testRequest)),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID
                )
            ).thenReturn(Observable.from([dataResponse, dataResponseSecond]))
        }
        let response = try service?.getValueUpdates(request: testRequest)
            .toBlocking()
            .toArray()
        XCTAssertEqual(
            response!.map { try! $0.serializedData() },
            [Data(), Data()]
        )
    }
}
