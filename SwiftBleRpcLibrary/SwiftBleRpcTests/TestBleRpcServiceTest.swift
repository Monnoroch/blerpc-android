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
        bleRpcDriverMock = MockBleServiceDriver(peripheral: CentralManagerSwizzle.instance.peripheral()!, connected: true)
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
                    request: try Device_GetValueRequest.bleRpcEncode(proto: testRequest),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID))
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
                    request: try Device_GetValueRequest.bleRpcEncode(proto: testRequest),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID)).thenReturn(Single<Data>.just(Data()))
        }

        var seenError = false
        dispose = service?.readValue(request: testRequest).subscribe(onSuccess: {}) { (error) in
            seenError = true
            expect(String(reflecting: error)).to(equal(String(reflecting: ProtoParserErrors.wrongData)))
        }

        XCTAssertTrue(seenError)
    }

    func testWrite() throws {
        let testRequest = Device_SetValueRequest().with({ (request) in
            request.intValue = 45
        })
        let testResponse = Device_SetValueResponse()

        stub(bleRpcDriverMock) { stub in
            when(
                stub.write(
                    request: try Device_SetValueRequest.bleRpcEncode(proto: testRequest),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID))
                .thenReturn(Single<Data>.just(try Device_SetValueResponse.bleRpcEncode(proto: testResponse)))
        }

        let response = try service?.writeValue(request: testRequest)
            .toBlocking()
            .first()

        XCTAssertEqual(response, responseMessage)
    }

    func testSubscribe() throws {
        let testRequest = Device_GetValueRequest()
        let testResponse = Device_GetValueResponse.with({ (response) in
            response.intValue = 94
        })

        stub(bleRpcDriverMock) { stub in
            when(
                stub.subscribe(
                    request: try Device_SetValueRequest.bleRpcEncode(proto: testRequest),
                    serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID))
                .thenReturn(Observable<Data>.just(try Device_SetValueResponse.bleRpcEncode(proto: testResponse)))
        }

        let response = try service?.getValueUpdates(request: testRequest)
            .toBlocking()
            .first()

        XCTAssertEqual(response, responseMessage)
    }

    func testSubscribeMultipleValues() throws {
        // TODO(korepanov): implement test with returning two different values to the subscription.
    }
}
