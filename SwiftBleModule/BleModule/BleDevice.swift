import Foundation
import RxBluetoothKit

/// High Level BLE Device
public class BleDevice {

    // MARK: - Variables
    
    /// Device identifier, usually as UUID string
    public let identifier: String
    
    /// Device name
    public let name: String
    
    /// Internal Device peripheral description
    internal let peripheral: Peripheral

    // MARK: - Initializers
    
    /// Init from *ScannedPeripheral*
    /// - parameter peripheral: peripheral received from scan
    /// - returns: BleDevice
    public init(peripheral: ScannedPeripheral) {
        self.name = peripheral.peripheral.name ?? "Unknown Device"
        self.identifier = peripheral.peripheral.identifier.uuidString
        self.peripheral = peripheral.peripheral
    }

}
