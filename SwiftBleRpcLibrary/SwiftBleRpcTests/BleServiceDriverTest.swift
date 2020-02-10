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
    static let testText = "A0000000-0000-0000-0000-000000000000"
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

    override var services: [CBService]? {
        return [service]
    }

    override var state: CBPeripheralState {
        return .connected
    }

    override var identifier: UUID {
        return TestConstants.uuid
    }

    init(arrayString: [String]) {
        service = CBServiceMock(arrayString: arrayString)
    }

    override func readValue(for characteristic: CBCharacteristic) {
        delegate?.peripheral?(self, didUpdateValueFor: characteristic, error: nil)
    }

    override func writeValue(_ data: Data, for characteristic: CBCharacteristic, type: CBCharacteristicWriteType) {
        delegate?.peripheral?(self, didWriteValueFor: characteristic, error: nil)
    }

    override func writeValue(_ data: Data, for descriptor: CBDescriptor) {
        delegate?.peripheral?(self, didWriteValueFor: descriptor, error: nil)
    }

    override func setNotifyValue(_ enabled: Bool, for characteristic: CBCharacteristic) {
        delegate?.peripheral?(self, didUpdateValueFor: characteristic, error: nil)
    }
}

extension CBCentralManager {
    static let peripheral: [CBPeripheral] = [CBPeripheralMock(arrayString: [TestConstants.testText])]
    func swizzle(){
        let swizzleClosure: () -> () = {
            let originalRetrievePeripheralsSelector = #selector(CBCentralManager.retrievePeripherals(withIdentifiers:))
            let swizzledRetrievePeripheralsSelector = #selector(CBCentralManager.retrievePeripheralsSwizzling(withIdentifiers:))
            let originalRetrievePeripheralsMethod = class_getInstanceMethod(CBCentralManager.self, originalRetrievePeripheralsSelector)
            let swizzledRetrievePeripheralsMethod = class_getInstanceMethod(CBCentralManager.self, swizzledRetrievePeripheralsSelector)
            method_exchangeImplementations(originalRetrievePeripheralsMethod!, swizzledRetrievePeripheralsMethod!)
            
            let originalScanForPeripheralsSelector = #selector(CBCentralManager.scanForPeripherals(withServices:options:))
            let swizzledScanForPeripheralsSelector = #selector(CBCentralManager.scanForPeripheralsSwizzling(withServices:options:))
            let originalScanForPeripheralsMethod = class_getInstanceMethod(CBCentralManager.self, originalScanForPeripheralsSelector)
            let swizzledScanForPeripheralsMethod = class_getInstanceMethod(CBCentralManager.self, swizzledScanForPeripheralsSelector)
            method_exchangeImplementations(originalScanForPeripheralsMethod!, swizzledScanForPeripheralsMethod!)
            
            let originalConnectSelector = #selector(CBCentralManager.stopScan)
            let swizzledConnectSelector = #selector(CBCentralManager.stopScanSwizzling)
            let originalConnectMethod = class_getInstanceMethod(CBCentralManager.self, originalConnectSelector)
            let swizzledConnectMethod = class_getInstanceMethod(CBCentralManager.self, swizzledConnectSelector)
            method_exchangeImplementations(originalConnectMethod!, swizzledConnectMethod!)
        }
        swizzleClosure()
    }

    open override var state: CBManagerState {
        return .poweredOn
    }

    @objc func retrievePeripheralsSwizzling(withIdentifiers identifiers: [UUID]) -> [CBPeripheral] {
        return CBCentralManager.peripheral
    }

    @objc func scanForPeripheralsSwizzling(withServices serviceUUIDs: [CBUUID]?, options: [String : Any]? = nil) {
        delegate?.centralManager?(self, didDiscover: CBCentralManager.peripheral.first!, advertisementData: [:], rssi: 1.0)
    }

    @objc func stopScanSwizzling() {
        delegate?.centralManager?(self, didDisconnectPeripheral: CBCentralManager.peripheral.first!, error: nil)
    }
}

class BleServiceDriverTest: XCTestCase {
    
    static var centralManager: CentralManager!
    
    override class func setUp() {
        centralManager = CentralManager(queue: .main)
        centralManager.manager.swizzle()
        super.setUp()
    }

    func testRead() {
        let result = try! BleServiceDriverTest.centralManager.scanForPeripherals(withServices: nil).toBlocking().first()
        let uuid = result?.peripheral.identifier.uuidString
        let driver = BleServiceDriver(
            peripheral: result!.peripheral,
            queue: DispatchQueue(label: "BleServiceDriverRead", qos: .background),
            connected: true
        )
        let resultRead = try! driver.read(
            request: Data(),
            serviceUUID: uuid!,
            characteristicUUID: uuid!
        ).toBlocking(timeout: 5).first()
        driver.disconnect()
        XCTAssertEqual(TestConstants.testText, String(data: resultRead!, encoding: .utf8))
    }
    
    func testWrite() {
        let result = try! BleServiceDriverTest.centralManager.scanForPeripherals(withServices: nil).toBlocking().first()
        let uuid = result?.peripheral.identifier.uuidString
        let driver = BleServiceDriver(
            peripheral: result!.peripheral,
            queue: DispatchQueue(label: "BleServiceDriverWrite", qos: .background),
            connected: true
        )
        let writeResponse = try! driver.write(
            request: Data(),
            serviceUUID: uuid!,
            characteristicUUID: uuid!
        ).toBlocking(timeout: 5).first()
        driver.disconnect()
        XCTAssertEqual(Data(), writeResponse!)
    }
    
    func testSubscribe() {
        let result = try! BleServiceDriverTest.centralManager.scanForPeripherals(withServices: nil).toBlocking().first()
        let uuid = result?.peripheral.identifier.uuidString
        let driver = BleServiceDriver(
            peripheral: result!.peripheral,
            queue: DispatchQueue(label: "BleServiceDriverSubscribe", qos: .background),
            connected: true
        )
        let subscribeResponse = try! driver.subscribe(
            request: Data(),
            serviceUUID: uuid!,
            characteristicUUID: uuid!
        ).toBlocking(timeout: 5).first()
        driver.disconnect()
        XCTAssertEqual(TestConstants.testText, String(data: subscribeResponse!, encoding: .utf8))
    }
}
