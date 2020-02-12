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

    let bleRpcDriverMock: MockBleServiceDriver = MockBleServiceDriver()
    let characteristicUUID: String = "A0000001-0000-0000-0000-000000000000"
    let readRequest = Device_GetValueRequest()
    var writeRequest = Device_SetValueRequest()
    var service: TestService?
    var dispose: Disposable?

    override func setUp() {
        super.setUp()
        service = TestService.init(bleRpcDriverMock)
    }

    override func tearDown() {
        clearStubs(bleRpcDriverMock)
        dispose?.dispose()
    }

    /**
    *  Read value test method.
    */
    func testRead() throws {
        let requestData = try Device_GetValueRequest.bleRpcEncode(proto: readRequest)
        let responseMessage = Device_GetValueResponse.with({ (response) in
            response.intValue = 94
        })
        let responseData = try Device_GetValueResponse.bleRpcEncode(proto: responseMessage)

        stub(bleRpcDriverMock) { stub in
            when(
                stub.read(
                    request: requestData, serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID)).thenReturn(Single<Data>.just(responseData))
        }

        let result = try service?.readValue(request: readRequest)
            .toBlocking()
            .first()

        XCTAssertEqual(result, responseMessage)
    }

    /**
    *  Read method called test method.
    */
    func testReadWasCalled() throws {
        let requestData = try Device_GetValueRequest.bleRpcEncode(proto: readRequest)
        let responseMessage = Device_GetValueResponse.with({ (response) in
            response.intValue = 94
        })
        let responseData = try Device_GetValueResponse.bleRpcEncode(proto: responseMessage)

        stub(bleRpcDriverMock) { stub in
            when(
                stub.read(
                    request: requestData, serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID)).thenReturn(Single<Data>.just(responseData))
        }

        let result = try service?.readValue(request: readRequest)
            .asObservable()
            .timeout(5, scheduler: MainScheduler.instance)
            .toBlocking().first()

        XCTAssertNotNil(result)
    }

    /**
    *  Wrong response handling test method.
    */
    func testWrongResponse() throws {
        let requestData = try Device_GetValueRequest.bleRpcEncode(proto: readRequest)
        let responseMessage = Device_GetValueResponse.with({ (response) in
            response.intValue = 94
        })

        stub(bleRpcDriverMock) { stub in
            when(
                stub.read(
                    request: requestData, serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID)).thenReturn(Single<Data>.just(Data()))
        }

        dispose = service?.readValue(request: readRequest).subscribe(onSuccess: { (response) in
            expect(response).to(equal(responseMessage))
        }) { (error) in
            expect(String(reflecting: error)).to(equal(String(reflecting: ProtoParserErrors.wrongData)))
        }
    }

    /**
    *  Write value test method.
    */
    func testWrite() throws {
        writeRequest.intValue = 45
        let requestData = try Device_SetValueRequest.bleRpcEncode(proto: writeRequest)
        let responseMessage = Device_SetValueResponse()
        let responseData = try Device_SetValueResponse.bleRpcEncode(proto: responseMessage)

        stub(bleRpcDriverMock) { stub in
            when(
                stub.write(
                    request: requestData, serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID)).thenReturn(Single<Data>.just(responseData))
        }

        let result = try service?.writeValue(request: writeRequest)
            .toBlocking()
            .first()

        XCTAssertEqual(result, responseMessage)
    }

    /**
    *  Subscribe test method.
    */
    func testSubscribe() throws {
        let requestData = try Device_GetValueRequest.bleRpcEncode(proto: readRequest)
        var int = 94
        let responseMessage = try Device_GetValueResponse.bleRpcDecode(
            data: Data(bytes: &int, count: MemoryLayout<Int>.size))
        let responseData = try Device_GetValueResponse.bleRpcEncode(proto: responseMessage)

        stub(bleRpcDriverMock) { stub in
            when(
                stub.subscribe(
                    request: requestData, serviceUUID: TestService.TestServiceUUID,
                    characteristicUUID: characteristicUUID)).thenReturn(Observable<Data>.just(responseData))
        }

        let result = try service?.getValueUpdates(request: readRequest)
            .toBlocking()
            .first()

        XCTAssertEqual(result, responseMessage)
    }

}
