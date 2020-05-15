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
    var testResponseLongData: Data = Data()
    var testResponseLong = Device_GetValueResponse.with { response in
        response.intValue = 94
    }
    var service: TestService?
    var dispose: Disposable?

    override func setUp() {
        super.setUp()
        testResponseLongData = try! Device_GetValueResponse.bleRpcEncode(proto: testResponseLong)
        testResponseLongData.append(Data(hex: "longData"))
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
        stub(bleRpcDriverMock) { stub in
            when(
                stub.read(
                    request: equal(to: try! Device_GetValueRequest.bleRpcEncode(proto: testRequest)),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID
                )
            )
                .thenReturn(.just(testResponseLongData))
        }

        let response = try service?.readValue(request: testRequest)
            .toBlocking()
            .first()

        XCTAssertEqual(response, testResponseLong)
    }

    func testWriteLongData() throws {
        let testRequest = Device_SetValueRequest.with { request in
            request.intValue = 45
        }
        let testResponse = Device_SetValueResponse()
        var longDataResponse = try! Device_SetValueResponse.bleRpcEncode(proto: testResponse)
        longDataResponse.append(Data(hex: "longData"))
        stub(bleRpcDriverMock) { stub in
            when(
                stub.write(
                    request: equal(to: try! Device_SetValueRequest.bleRpcEncode(proto: testRequest)),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID
                )
            )
                .thenReturn(.just(longDataResponse))
        }

        let response = try service?.writeValue(request: testRequest)
            .toBlocking()
            .first()
        XCTAssertEqual(response, testResponse)
    }

    func testSubscribeLongData() throws {
        let testRequest = Device_GetValueRequest()
        stub(bleRpcDriverMock) { stub in
            when(
                stub.subscribe(
                    request: equal(to: try! Device_GetValueRequest.bleRpcEncode(proto: testRequest)),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID
                )
            )
                .thenReturn(.just(testResponseLongData))
        }
        let response = try service?.getValueUpdates(request: testRequest)
            .toBlocking()
            .first()
        XCTAssertEqual(response, testResponseLong)
    }

    func testSubscribeMultipleValuesLongData() throws {
        let testRequest = Device_GetValueRequest()
        let responseArray = [testResponseLong, testResponseLong]
        stub(bleRpcDriverMock) { stub in
            when(
                stub.subscribe(
                    request: equal(to: try! Device_GetValueRequest.bleRpcEncode(proto: testRequest)),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID
                )
            )
                .thenReturn(.from([testResponseLongData, testResponseLongData]))
        }
        let response = try service?.getValueUpdates(request: testRequest)
            .toBlocking()
            .toArray()
        XCTAssertEqual(response, responseArray)
    }
}
