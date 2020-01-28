import XCTest
import Nimble
import Cuckoo
import RxSwift
@testable import SwiftBleRpc

/**
*  A service for testing BleRpcService.
*/
class TestBleRpcServiceTest: XCTestCase {
    
    let bleRpcDriverMock: MockBleServiceDriver = MockBleServiceDriver(queue: DispatchQueue(label: "test_ble_queue"))
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
        let responseMessage = Device_GetValueResponse()
        let responseData = try Device_GetValueResponse.bleRpcEncode(proto: responseMessage)
        
        stub(bleRpcDriverMock) { stub in
            when(stub.read(request: requestData, serviceUUID: TestService.TestServiceUUID, characteristicUUID: characteristicUUID)).thenReturn(Single<Data>.just(responseData))
        }
        
        dispose = service?.readValue(request: readRequest).subscribe(onSuccess: { (response) in
            expect(response).to(equal(responseMessage))
        }) { (error) in
            expect(error).to(beNil())
        }
    }
    
    /**
    *  Read method called test method.
    */
    func testReadWasCalled() throws {
        let requestData = try Device_GetValueRequest.bleRpcEncode(proto: readRequest)
        let responseMessage = Device_GetValueResponse()
        let responseData = try Device_GetValueResponse.bleRpcEncode(proto: responseMessage)
        
        stub(bleRpcDriverMock) { stub in
            when(stub.read(request: requestData, serviceUUID: TestService.TestServiceUUID, characteristicUUID: characteristicUUID)).thenReturn(Single<Data>.just(responseData))
        }
        
        let exp = expectation(description: "Read was called")
        dispose = service?.readValue(request: readRequest).subscribe(onSuccess: { (response) in
            exp.fulfill()
        }) { (error) in
            exp.fulfill()
        }
        
        waitForExpectations(timeout: 3)
    }
    
    /**
    *  Wrong response handling test method.
    */
    func testWrongResponse() throws {
        let requestData = try Device_GetValueRequest.bleRpcEncode(proto: readRequest)
        let responseMessage = Device_GetValueResponse()
        
        stub(bleRpcDriverMock) { stub in
            when(stub.read(request: requestData, serviceUUID: TestService.TestServiceUUID, characteristicUUID: characteristicUUID)).thenReturn(Single<Data>.just(Data()))
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
            when(stub.write(request: requestData, serviceUUID: TestService.TestServiceUUID, characteristicUUID: characteristicUUID)).thenReturn(Single<Data>.just(responseData))
        }
        
        dispose = service?.writeValue(request: writeRequest).subscribe(onSuccess: { (response) in
            expect(response).to(equal(responseMessage))
        }) { (error) in
            expect(error).to(beNil())
        }
    }
    
    /**
    *  Subscribe test method.
    */
    func testSubscribe() throws {
        let requestData = try Device_GetValueRequest.bleRpcEncode(proto: readRequest)
        let responseMessage = Device_GetValueResponse()
        let responseData = try Device_GetValueResponse.bleRpcEncode(proto: responseMessage)
        
        stub(bleRpcDriverMock) { stub in
            when(stub.subscribe(request: requestData, serviceUUID: TestService.TestServiceUUID, characteristicUUID: characteristicUUID)).thenReturn(Observable<Data>.just(responseData))
        }
        
        dispose = service?.getValueUpdates(request: readRequest).subscribe(onNext: { (response) in
            expect(response).to(equal(responseMessage))
        }, onError: { (error) in
            expect(error).to(beNil())
        })
    }
    
}
