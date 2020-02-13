import XCTest
import Nimble
import Cuckoo
import RxBlocking
import RxSwift
@testable import SwiftBleRpc

/**
*  A service for testing BleRpcService.
*/
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

    func testReadInvalidResponse() throws {
        let testRequest = Device_GetValueRequest()

        stub(bleRpcDriverMock) { stub in
            when(
                stub.read(
                    request: equal(to: try! Device_GetValueRequest.bleRpcEncode(proto: testRequest)),
                    serviceUUID: equal(to: TestService.TestServiceUUID),
                    characteristicUUID: equal(to: characteristicUUID)
                )
            ).thenReturn(.just(Data()))
        }

        var seenError = false
        XCTAssertThrowsError(try service?.readValue(request: testRequest).toBlocking().first())
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
}
