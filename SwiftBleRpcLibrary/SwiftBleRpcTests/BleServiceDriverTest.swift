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
        delegate?.peripheral?(self, didUpdateValueFor: characteristic, error: nil)
        delegate?.peripheral?(self, didUpdateValueFor: characteristic, error: nil)
    }
}

extension CBCentralManager {
    static let peripheral: [CBPeripheral] = [CBPeripheralMock(arrayString: [TestConstants.testText])]
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

    override func setUp() {
        peripheral = CentralManagerSwizzle.instance.peripheral()
        uuid = peripheral!.identifier.uuidString
        bleServiceDriver = BleServiceDriver(
            peripheral: peripheral!
        )
        super.setUp()
    }

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

    func testDisconnectMultipleSubscriptionConnection() {
        let firstDisposeBag = DisposeBag()
        let secondDisposeBag = DisposeBag()
        var disposedFirst = false
        var disposedSecond = false
        let expectationEventFirst = expectation(description: "Expect first dispose connection")
        let expectationEventSecond = expectation(description: "Expect second dispose connection")
        try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).subscribe(
            onDisposed: {
                disposedFirst = true
                expectationEventFirst.fulfill()
            }
        ).disposed(by: firstDisposeBag)
        try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).subscribe(
            onDisposed: {
                disposedSecond = true
                expectationEventSecond.fulfill()
            }
        ).disposed(by: secondDisposeBag)
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertTrue(disposedFirst, "Not disconnect first connection")
        XCTAssertTrue(disposedSecond, "Not disconnect second connection")
    }
    
    func testDisconnectReadConnection() {
        let disposeBag = DisposeBag()
        var disposedAll = false
        let expectationEvent = expectation(description: "Expect dispose connection")
        try! bleServiceDriver.read(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(onDispose: {
            disposedAll = true
            expectationEvent.fulfill()
        }).subscribe().disposed(by: disposeBag)
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertTrue(disposedAll, "Not disconnect")
    }
    
    func testDisconnectWriteConnection() {
        let disposeBag = DisposeBag()
        var disposedAll = false
        let expectationEvent = expectation(description: "Expect dispose connection")
        try! bleServiceDriver.write(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(onDispose: {
            disposedAll = true
            expectationEvent.fulfill()
        }).subscribe().disposed(by: disposeBag)
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertTrue(disposedAll, "Not disconnect")
    }

    func testDisconnectReadWriteConnection() {
        let disposeBagFirst = DisposeBag()
        let disposeBagSecond = DisposeBag()
        var disposedFirst = false
        var disposedSecond = false
        let expectationEventFirst = expectation(description: "Expect dispose connection")
        let expectationEventSecond = expectation(description: "Expect dispose connection")
        bleServiceDriver.write(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(onDispose: {
            disposedFirst = true
            expectationEventFirst.fulfill()
        }).subscribe().disposed(by: disposeBagFirst)
        try! bleServiceDriver.read(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(onDispose: {
            disposedSecond = true
            expectationEventSecond.fulfill()
        }).subscribe().disposed(by: disposeBagSecond)
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertTrue(disposedFirst, "Not first disconnect")
        XCTAssertTrue(disposedSecond, "Not second disconnect")
    }
    
    func testDisconnectReadWriteSubscriptionConnection() {
        let disposeBagFirst = DisposeBag()
        let disposeBagSecond = DisposeBag()
        let disposeBagThird = DisposeBag()
        var disposedFirst = false
        var disposedSecond = false
        var disposedThird = false
        let expectationEventFirst = expectation(description: "Expect dispose connection")
        let expectationEventSecond = expectation(description: "Expect dispose connection")
        let expectationEventThird = expectation(description: "Expect dispose connection")
        bleServiceDriver.write(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(onDispose: {
            disposedFirst = true
            expectationEventFirst.fulfill()
        }).subscribe().disposed(by: disposeBagFirst)
        try! bleServiceDriver.read(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(onDispose: {
            disposedSecond = true
            expectationEventSecond.fulfill()
        }).subscribe().disposed(by: disposeBagSecond)
        try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).subscribe(
            onDisposed: {
                disposedThird = true
                expectationEventThird.fulfill()
            }
        ).disposed(by: disposeBagThird)
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertTrue(disposedFirst, "Not first disconnect")
        XCTAssertTrue(disposedSecond, "Not second disconnect")
        XCTAssertTrue(disposedThird, "Not third disconnect")
    }

    func testDisconnectAfterDisposeReadConnection() {
        var disposedAll = false
        let expectationEvent = expectation(description: "Expect dispose connection")
        _ = try! bleServiceDriver.read(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(onDispose: {
            disposedAll = true
            expectationEvent.fulfill()
            }).toBlocking().first()!
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertTrue(disposedAll, "Not disconnect")
    }

    func testDisconnectAfterDisposeWriteConnection() {
        var disposedAll = false
        let expectationEvent = expectation(description: "Expect dispose connection")
        _ = try! bleServiceDriver.write(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(onDispose: {
            disposedAll = true
            expectationEvent.fulfill()
            }).toBlocking().first()!
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertTrue(disposedAll, "Not disconnect")
    }

    func testDisconnectAfterSubscriptionDisposeConnection() {
        var disposeBag = DisposeBag()
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
        disposeBag = DisposeBag()
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertTrue(disposedAll, "Not disconnect")
    }

    func testEstablishConnection() {
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

    func testEstablishConnectionAfterDisconnected() {
        (peripheral.peripheral as? CBPeripheralMock)?.peripheralState = .disconnected
        XCTAssertFalse(peripheral.isConnected, "Peripheral connected")
        _ = try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
            ).toBlocking(timeout: 5).first()
        XCTAssertTrue(peripheral.isConnected, "Peripheral disconnected")
    }

    func testEstablishConnectionAfterMultipleDisconnect() {
        (peripheral.peripheral as? CBPeripheralMock)?.peripheralState = .disconnected
        XCTAssertFalse(peripheral.isConnected, "Peripheral connected")
        _ = try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
            ).toBlocking(timeout: 5).first()
        (peripheral.peripheral as? CBPeripheralMock)?.peripheralState = .disconnected
        XCTAssertFalse(peripheral.isConnected, "Peripheral connected")
        _ = try! bleServiceDriver.write(
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
    
    func testDisposeBeforeDisconnect() {
        var disposeBag = DisposeBag()
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
        disposeBag = DisposeBag()
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertTrue(disposedAll, "Not disconnect")
    }

    func testDisconnectBeforeDispose() {
        var disposeBag = DisposeBag()
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
        disposeBag = DisposeBag()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertTrue(disposedAll, "Not disconnect")
    }
    
    func testDisposeBeforeDisconnectRead() {
        var disposedAll = false
        var disposeBag = DisposeBag()
        let expectationEvent = expectation(description: "Expect dispose connection")
        try! bleServiceDriver.read(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(onDispose: {
            disposedAll = true
            expectationEvent.fulfill()
            }).subscribe().disposed(by: disposeBag)
        disposeBag = DisposeBag()
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertTrue(disposedAll, "Not disconnect")
    }
    
    func testDisconnectBeforeDisposeRead() {
        var disposedAll = false
        var disposeBag = DisposeBag()
        let expectationEvent = expectation(description: "Expect dispose connection")
        try! bleServiceDriver.read(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(onDispose: {
            disposedAll = true
            expectationEvent.fulfill()
            }).subscribe().disposed(by: disposeBag)
        bleServiceDriver.disconnect()
        disposeBag = DisposeBag()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertTrue(disposedAll, "Not disconnect")
    }

    func testDisposeBeforeDisconnectWrite() {
        var disposedAll = false
        var disposeBag = DisposeBag()
        let expectationEvent = expectation(description: "Expect dispose connection")
        bleServiceDriver.write(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(onDispose: {
            disposedAll = true
            expectationEvent.fulfill()
            }).subscribe().disposed(by: disposeBag)
        disposeBag = DisposeBag()
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertTrue(disposedAll, "Not disconnect")
    }

    func testDisconnectBeforeDisposeWrite() {
        var disposedAll = false
        var disposeBag = DisposeBag()
        let expectationEvent = expectation(description: "Expect dispose connection")
        bleServiceDriver.write(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).do(onDispose: {
            disposedAll = true
            expectationEvent.fulfill()
            }).subscribe().disposed(by: disposeBag)
        bleServiceDriver.disconnect()
        disposeBag = DisposeBag()
        waitForExpectations(timeout: 5, handler: nil)
        XCTAssertTrue(disposedAll, "Not disconnect")
    }
    
    func testEstablishOneConnection() {
        _ = try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).toBlocking(timeout: 5).first()
        XCTAssertTrue(peripheral.isConnected, "Peripheral disconnected")
    }

    func testEstablishConnectionMultipleConnection() {
        _ = try! bleServiceDriver.subscribe(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).toBlocking(timeout: 5).first()!
        _ = try! bleServiceDriver.write(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).toBlocking().first()!
        _ = try! bleServiceDriver.read(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
        ).toBlocking().first()!
        XCTAssertTrue(peripheral.isConnected, "Peripheral disconnected")
    }

    func testDisconnectRead() {
        var errorDisconnect: Error? = nil
        let disposeBag = DisposeBag()
        let expectationEvent = expectation(description: "Expect dispose connection")
        (peripheral.peripheral as? CBPeripheralMock)?.waitReadOperation = true
        try! bleServiceDriver.read(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
            ).do(onError: { error in
            errorDisconnect = error
            expectationEvent.fulfill()
        }).subscribe().disposed(by: disposeBag)
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        if let errorDisconnect = errorDisconnect as? BleServiceDriverErrors {
            XCTAssertEqual(errorDisconnect, BleServiceDriverErrors.disconnected, "Canceled operation")
        } else {
            XCTFail("Not return error")
        }
        (peripheral.peripheral as? CBPeripheralMock)?.waitReadOperation = false
    }

    func testDisconnectWrite() {
        var errorDisconnect: Error? = nil
        let disposeBag = DisposeBag()
        let expectationEvent = expectation(description: "Expect dispose connection")
        (peripheral.peripheral as? CBPeripheralMock)?.waitWriteOperation = true
        bleServiceDriver.write(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
            ).do(onError: { error in
            errorDisconnect = error
            expectationEvent.fulfill()
        }).subscribe().disposed(by: disposeBag)
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        if let errorDisconnect = errorDisconnect as? BleServiceDriverErrors {
            XCTAssertEqual(errorDisconnect, BleServiceDriverErrors.disconnected, "Canceled operation")
        } else {
            XCTFail("Not return error")
        }
        (peripheral.peripheral as? CBPeripheralMock)?.waitWriteOperation = false
    }
    
    func testDisconnectMultipleOperation() {
        var errorDisconnectRead: Error? = nil
        var errorDisconnectWrite: Error? = nil
        let disposeBagRead = DisposeBag()
        let disposeBagWrite = DisposeBag()
        let expectationEventRead = expectation(description: "Expect disconnect")
        let expectationEventWrite = expectation(description: "Expect disconnect")
        (peripheral.peripheral as? CBPeripheralMock)?.waitWriteOperation = true
        (peripheral.peripheral as? CBPeripheralMock)?.waitReadOperation = true
        try? bleServiceDriver.read(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
            ).do(onError: { error in
            errorDisconnectRead = error
            expectationEventRead.fulfill()
        }).subscribe().disposed(by: disposeBagRead)
        bleServiceDriver.write(
            request: Data(),
            serviceUUID: uuid,
            characteristicUUID: uuid
            ).do(onError: { error in
            errorDisconnectWrite = error
            expectationEventWrite.fulfill()
        }).subscribe().disposed(by: disposeBagWrite)
        bleServiceDriver.disconnect()
        waitForExpectations(timeout: 5, handler: nil)
        if let errorDisconnect = errorDisconnectRead as? BleServiceDriverErrors {
            XCTAssertEqual(errorDisconnect, BleServiceDriverErrors.disconnected, "Canceled operation")
        } else {
            XCTFail("Not return error")
        }
        if let errorDisconnect = errorDisconnectWrite as? BleServiceDriverErrors {
            XCTAssertEqual(errorDisconnect, BleServiceDriverErrors.disconnected, "Canceled operation")
        } else {
            XCTFail("Not return error")
        }
        (peripheral.peripheral as? CBPeripheralMock)?.waitWriteOperation = false
        (peripheral.peripheral as? CBPeripheralMock)?.waitReadOperation = false
    }
}
