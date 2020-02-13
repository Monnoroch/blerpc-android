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
        delegate?.peripheral?(self, didUpdateValueFor: characteristic, error: nil)
    }
}

extension CBCentralManager {
    static let peripheral: [CBPeripheral] = [CBPeripheralMock(arrayString: [TestConstants.testText])]
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

    override func setUp() {
        let peripheral = CentralManagerSwizzle.instance.peripheral()
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
}
