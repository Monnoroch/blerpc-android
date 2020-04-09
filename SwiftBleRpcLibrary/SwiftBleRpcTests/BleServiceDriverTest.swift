import XCTest
import Nimble
import Cuckoo
import RxBlocking
import RxSwift
import CoreBluetooth
import RxBluetoothKit
@testable import SwiftBleRpc

class TestConstants {
    static let uuid = UUID()
    static let testText = "test data"
}

class CBCharacteristicMock: CBCharacteristic {

    var testText: String = ""

    override var value: Data? {
        return testText.data(using: .utf8)
    }

    override var uuid: CBUUID {
        return CBUUID(nsuuid: TestConstants.uuid)
    }

    init(text: String) {
        testText = text
    }
}

class CBServiceMock: CBService {

    var characteristicsTest: [CBCharacteristicMock]

    override var characteristics: [CBCharacteristic]? {
        return characteristicsTest
    }

    override var uuid: CBUUID {
        return CBUUID(nsuuid: TestConstants.uuid)
    }

    init(arrayString: [String]) {
        characteristicsTest = arrayString.map { CBCharacteristicMock(text: $0) }
    }
}

class CBPeripheralMock: CBPeripheral {

    var service: CBService
    var peripheralState: CBPeripheralState = .connected
    var waitReadOperation = false
    var waitWriteOperation = false
    var sendMultipleValue = false

    override var services: [CBService]? {
        return [service]
    }

    override var state: CBPeripheralState {
        return peripheralState
    }

    override var identifier: UUID {
        return TestConstants.uuid
    }

    init(arrayString: [String]) {
        service = CBServiceMock(arrayString: arrayString)
    }

    override func readValue(for characteristic: CBCharacteristic) {
        if !waitReadOperation {
            delegate?.peripheral?(self, didUpdateValueFor: characteristic, error: nil)
        }
    }

    override func writeValue(_ data: Data, for characteristic: CBCharacteristic, type: CBCharacteristicWriteType) {
        if !waitWriteOperation {
            delegate?.peripheral?(self, didWriteValueFor: characteristic, error: nil)
        }
    }

    override func writeValue(_ data: Data, for descriptor: CBDescriptor) {
        if !waitWriteOperation {
            delegate?.peripheral?(self, didWriteValueFor: descriptor, error: nil)
        }
    }

    override func setNotifyValue(_ enabled: Bool, for characteristic: CBCharacteristic) {
        if !waitReadOperation {
            if sendMultipleValue {
                delegate?.peripheral?(self, didUpdateValueFor: characteristic, error: nil)
            }
            delegate?.peripheral?(self, didUpdateValueFor: characteristic, error: nil)
        }
    }
}

extension CBCentralManager {
    static var peripheral: [CBPeripheral] {
        return [CBPeripheralMock(arrayString: [TestConstants.testText])]
    }
    static let error: Error? = nil
    func swizzle() {
        func setupSwizzlingMethod(method: Selector, swizzledMethod: Selector) {
            let originalMethod = class_getInstanceMethod(CBCentralManager.self, method)
            let swizzledMethod = class_getInstanceMethod(CBCentralManager.self, swizzledMethod)
            method_exchangeImplementations(originalMethod!, swizzledMethod!)
        }
        let swizzleClosure = {
            setupSwizzlingMethod(
                method: #selector(CBCentralManager.retrievePeripherals(withIdentifiers:)),
                swizzledMethod: #selector(CBCentralManager.retrievePeripheralsSwizzling(withIdentifiers:))
            )
            setupSwizzlingMethod(
                method: #selector(CBCentralManager.scanForPeripherals(withServices:options:)),
                swizzledMethod: #selector(CBCentralManager.scanForPeripheralsSwizzling(withServices:options:))
            )
            setupSwizzlingMethod(
                method: #selector(CBCentralManager.stopScan),
                swizzledMethod: #selector(CBCentralManager.stopScanSwizzling)
            )
            setupSwizzlingMethod(
                method: #selector(CBCentralManager.connect),
                swizzledMethod: #selector(CBCentralManager.connectSwizzling)
            )
            setupSwizzlingMethod(
                method: #selector(CBCentralManager.cancelPeripheralConnection),
                swizzledMethod: #selector(CBCentralManager.cancelPeripheralConnectionSwizzling)
            )
        }
        swizzleClosure()
    }

    open override var state: CBManagerState {
        return .poweredOn
    }

    @objc func retrievePeripheralsSwizzling(withIdentifiers identifiers: [UUID]) -> [CBPeripheral] {
        return CBCentralManager.peripheral
    }

    @objc func scanForPeripheralsSwizzling(
        withServices serviceUUIDs: [CBUUID]?,
        options: [String: Any]? = nil
    ) {
        delegate?.centralManager?(
            self,
            didDiscover: CBCentralManager.peripheral.first!,
            advertisementData: [:],
            rssi: 1.0
        )
    }

    @objc func stopScanSwizzling() {
        delegate?.centralManager?(self, didDisconnectPeripheral: CBCentralManager.peripheral.first!, error: nil)
    }

    @objc func connectSwizzling(_ peripheral: CBPeripheral, options: [String : Any]? = nil) {
        if peripheral.state != .connected {
            (peripheral as? CBPeripheralMock)?.peripheralState = .connected
            delegate?.centralManager?(self, didConnect: peripheral)
            delegate?.centralManager?(self, didDisconnectPeripheral: peripheral, error: CBCentralManager.error)
        } else {
            (peripheral as? CBPeripheralMock)?.peripheralState = .connected
            delegate?.centralManager?(self, didConnect: peripheral)
        }
    }

    @objc func cancelPeripheralConnectionSwizzling(_ peripheral: CBPeripheral) {
        (peripheral as? CBPeripheralMock)?.peripheralState = .disconnected
        delegate?.centralManager?(self, didDisconnectPeripheral: peripheral, error: CBCentralManager.error)
    }
}

class CentralManagerSwizzle {
    static let instance = CentralManagerSwizzle()
    private var centralManager: CentralManager!

    func centralManagerInstance() -> CentralManager {
        if let centralManager = centralManager {
            return centralManager
        }
        centralManager = CentralManager(queue: .main)
        centralManager.manager.swizzle()
        return centralManager
    }

    func peripheral() -> Peripheral? {
        return centralManagerInstance().retrievePeripherals(withIdentifiers: [TestConstants.uuid]).first
    }
}

class BleServiceDriverTest: XCTestCase {

    var uuid: String = ""
    var bleServiceDriver: BleServiceDriver!
    var peripheral: Peripheral!

    // TODO(#101): Creating disconnect tests while read / write / subscription is running
    override func setUp() {
        peripheral = CentralManagerSwizzle.instance.peripheral()
        uuid = peripheral!.identifier.uuidString
        bleServiceDriver = BleServiceDriver(
            peripheral: peripheral!
        )
        super.setUp()
    }

    // MARK: Tests for data from read / write / subscribe

    func testRead() {
        let resultRead = try! bleServiceDriver.read(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).toBlocking(timeout: 5).first()
        XCTAssertEqual(TestConstants.testText, String(data: resultRead!, encoding: .utf8))
    }

    func testWrite() {
        let writeResponse = try! bleServiceDriver.write(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).toBlocking(timeout: 5).first()
        XCTAssertEqual(Data(), writeResponse!)
    }

    func testSubscribe() {
        let subscribeResponse = try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).toBlocking(timeout: 5).first()
        XCTAssertEqual(TestConstants.testText, String(data: subscribeResponse!, encoding: .utf8))
    }

    func testSubscribeMultipleValues() {
        let responseArray = [TestConstants.testText, TestConstants.testText]
        (peripheral.peripheral as? CBPeripheralMock)?.sendMultipleValue = true
        let subscribeResponse = try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
            ).take(2).toBlocking(timeout: 5).toArray()
        XCTAssertEqual(responseArray, subscribeResponse.map { String(data: $0, encoding: .utf8) })
    }

    func testDisconnectOneSubscriptionConnection() {
        let disposeBag = DisposeBag()
        var disposedAll = false
        let expectationEvent = expectation(description: "disposedAll")
        try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).subscribe(
            onDisposed: {
                disposedAll = true
                expectationEvent.fulfill()
        }
        ).disposed(by: disposeBag)
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertTrue(disposedAll, "Not disconnect")
    }

    // MARK: Disconnect tests

    func testDisconnectRead() {
        var errorDisconnect: Error? = nil
        let disposeBag = DisposeBag()
        var disposeRead = false
        let expectationEvent = expectation(description: "Expect dispose connection")
        let expectationDisposeEvent = expectation(description: "Expect dispose connection")
        (peripheral.peripheral as? CBPeripheralMock)?.waitReadOperation = true
        try! bleServiceDriver.read(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(
            onError: { error in
                errorDisconnect = error
                expectationEvent.fulfill()
        },
            onDispose: {
                disposeRead = true
                expectationDisposeEvent.fulfill()
        }).subscribe().disposed(by: disposeBag)
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertTrue(disposeRead, "Not desposed")
        XCTAssertNotNil(errorDisconnect!)
        XCTAssertEqual(errorDisconnect as! BleServiceDriverErrors, BleServiceDriverErrors.disconnected, "Canceled operation")
    }
    
    func testDisconnectWrite() {
        var errorDisconnect: Error? = nil
        let disposeBag = DisposeBag()
        var disposeWrite = false
        let expectationEvent = expectation(description: "Expect dispose connection")
        let expectationDisposeEvent = expectation(description: "Expect dispose connection")
        (peripheral.peripheral as? CBPeripheralMock)?.waitWriteOperation = true
        bleServiceDriver.write(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(
            onError: { error in
                errorDisconnect = error
                expectationEvent.fulfill()
        },
            onDispose: {
                disposeWrite = true
                expectationDisposeEvent.fulfill()
        }).subscribe().disposed(by: disposeBag)
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertTrue(disposeWrite, "Not desposed")
        XCTAssertNotNil(errorDisconnect!)
        XCTAssertEqual(errorDisconnect as! BleServiceDriverErrors, BleServiceDriverErrors.disconnected, "Canceled operation")
    }

    func testDisconnectReadWriteSubscriptionConnection() {
        let disposeBagFirst = DisposeBag()
        let disposeBagSecond = DisposeBag()
        let disposeBagThird = DisposeBag()
        let disposeBagFourth = DisposeBag()
        var disposedFirst = false
        var disposedSecond = false
        var disposedThird = false
        var disposedFourth = false
        (peripheral.peripheral as? CBPeripheralMock)?.waitWriteOperation = true
        (peripheral.peripheral as? CBPeripheralMock)?.waitReadOperation = true
        var errorDisconnectRead: Error? = nil
        var errorDisconnectWrite: Error? = nil
        let expectationEventFirst = expectation(description: "Expect dispose connection")
        let expectationEventSecond = expectation(description: "Expect dispose connection")
        [expectationEventFirst, expectationEventSecond].forEach { $0.expectedFulfillmentCount = 2 }
        let expectationEventThird = expectation(description: "Expect dispose connection")
        let expectationEventFourth = expectation(description: "Expect dispose connection")
        try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).subscribe(onDisposed: {
            disposedThird = true
            expectationEventThird.fulfill()
        }).disposed(by: disposeBagThird)
        try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).subscribe(onDisposed: {
            disposedFourth = true
            expectationEventFourth.fulfill()
        }).disposed(by: disposeBagFourth)
        bleServiceDriver.write(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(
            onError: { error in
                errorDisconnectWrite = error
                expectationEventFirst.fulfill()
        },
            onDispose: {
                disposedFirst = true
                expectationEventFirst.fulfill()
        }).subscribe().disposed(by: disposeBagFirst)
        try! bleServiceDriver.read(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(
            onError: { error in
                errorDisconnectRead = error
                expectationEventSecond.fulfill()
        },
            onDispose: {
                disposedSecond = true
                expectationEventSecond.fulfill()
        }).subscribe().disposed(by: disposeBagSecond)
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertTrue(disposedFirst, "Not first disconnect")
        XCTAssertTrue(disposedSecond, "Not second disconnect")
        XCTAssertTrue(disposedThird, "Not third disconnect")
        XCTAssertTrue(disposedFourth, "Not fourth disconnect")
        XCTAssertNotNil(errorDisconnectRead!)
        XCTAssertEqual(errorDisconnectRead as! BleServiceDriverErrors, BleServiceDriverErrors.disconnected, "Canceled operation")
        XCTAssertNotNil(errorDisconnectWrite!)
        XCTAssertEqual(errorDisconnectWrite as! BleServiceDriverErrors, BleServiceDriverErrors.disconnected, "Canceled operation")
    }

    // MARK: Establish connection tests

    func testEstablishConnection() {
        (peripheral.peripheral as? CBPeripheralMock)?.peripheralState = .disconnected
        XCTAssertFalse(peripheral.isConnected, "Peripheral connected")
        _ = try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).toBlocking(timeout: 5).first()
        XCTAssertTrue(peripheral.isConnected, "Peripheral disconnected")
    }

    func testEstablishConnectionAfterDisconnecting() {
        (peripheral.peripheral as? CBPeripheralMock)?.peripheralState = .disconnecting
        XCTAssertFalse(peripheral.isConnected, "Peripheral connected")
        _ = try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).toBlocking(timeout: 5).first()
        XCTAssertTrue(peripheral.isConnected, "Peripheral disconnected")
    }
    
    func testEstablishConnectionAfterConnecting() {
        (peripheral.peripheral as? CBPeripheralMock)?.peripheralState = .connecting
        XCTAssertFalse(peripheral.isConnected, "Peripheral connected")
        _ = try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).subscribe()
        XCTAssertFalse(peripheral.isConnected, "Peripheral not wait connection")
    }
    
    func testEstablishConnectionAfterConnected() {
        (peripheral.peripheral as? CBPeripheralMock)?.peripheralState = .connected
        XCTAssertTrue(peripheral.isConnected, "Peripheral disconnected")
        _ = try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).toBlocking(timeout: 5).first()
        XCTAssertTrue(peripheral.isConnected, "Peripheral disconnected")
    }
    
    // MARK: Reconnect test
    func testReconnect() {
        let disposeBagFirst = DisposeBag()
        let disposeBagSecond = DisposeBag()
        let disposeBagThird = DisposeBag()
        let disposeBagFourth = DisposeBag()
        (peripheral.peripheral as? CBPeripheralMock)?.waitWriteOperation = true
        (peripheral.peripheral as? CBPeripheralMock)?.waitReadOperation = true
        let expectationEventFirst = expectation(description: "Expect dispose connection")
        let expectationEventSecond = expectation(description: "Expect dispose connection")
        [expectationEventFirst, expectationEventSecond].forEach { $0.expectedFulfillmentCount = 2 }
        let expectationEventThird = expectation(description: "Expect dispose connection")
        let expectationEventFourth = expectation(description: "Expect dispose connection")
        try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).subscribe(onDisposed: {
            expectationEventThird.fulfill()
        }).disposed(by: disposeBagThird)
        try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).subscribe(onDisposed: {
            expectationEventFourth.fulfill()
        }).disposed(by: disposeBagFourth)
        bleServiceDriver.write(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(
            onError: { error in
                expectationEventFirst.fulfill()
        },
            onDispose: {
                expectationEventFirst.fulfill()
        }).subscribe().disposed(by: disposeBagFirst)
        try! bleServiceDriver.read(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(
            onError: { error in
                expectationEventSecond.fulfill()
        },
            onDispose: {
                expectationEventSecond.fulfill()
        }).subscribe().disposed(by: disposeBagSecond)
        XCTAssertTrue(peripheral.isConnected, "Peripheral disconnected")
        bleServiceDriver.disconnect()
        XCTAssertFalse(peripheral.isConnected, "Peripheral connected")
        waitForExpectations(timeout: 5, handler: nil)
        let expectationEventFirst1 = expectation(description: "Expect dispose connection")
        let expectationEventSecond1 = expectation(description: "Expect dispose connection")
        [expectationEventFirst1, expectationEventSecond1].forEach { $0.expectedFulfillmentCount = 2 }
        let expectationEventThird1 = expectation(description: "Expect dispose connection")
        let expectationEventFourth1 = expectation(description: "Expect dispose connection")
        try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).subscribe(onDisposed: {
            expectationEventThird1.fulfill()
        }).disposed(by: disposeBagThird)
        try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).subscribe(onDisposed: {
            expectationEventFourth1.fulfill()
        }).disposed(by: disposeBagFourth)
        bleServiceDriver.write(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(
            onError: { error in
                expectationEventFirst1.fulfill()
        },
            onDispose: {
                expectationEventFirst1.fulfill()
        }).subscribe().disposed(by: disposeBagFirst)
        try! bleServiceDriver.read(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(
            onError: { error in
                expectationEventSecond1.fulfill()
        },
            onDispose: {
                expectationEventSecond1.fulfill()
        }).subscribe().disposed(by: disposeBagSecond)
        XCTAssertTrue(peripheral.isConnected, "Peripheral disconnected")
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertFalse(peripheral.isConnected, "Peripheral connected")
    }
}
