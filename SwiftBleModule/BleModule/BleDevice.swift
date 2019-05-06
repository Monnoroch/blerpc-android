import Foundation
import RxBluetoothKit

/// High Level Ble Device
public class BleDevice {

    // MARK: - Variables
    
    /// Device identifier, usually as UUID string
    public let identifier: String
    
    /// Device name
    public let name: String?
    
    /// Internal Device peripheral description
    public let peripheral: Peripheral

    // MARK: - Initializers
    
    /// Init from *ScannedPeripheral*
    /// - parameter peripheral: peripheral received from scan
    /// - returns: BleDevice
    public init(peripheral: ScannedPeripheral) {
        self.name = peripheral.peripheral.name
        self.identifier = peripheral.peripheral.identifier.uuidString
        self.peripheral = peripheral.peripheral
    }

    /// Block default init
    private init() {
        fatalError()
    }
    
}
