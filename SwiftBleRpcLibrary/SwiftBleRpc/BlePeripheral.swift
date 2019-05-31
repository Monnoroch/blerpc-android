import Foundation
import RxBluetoothKit

/// High Level Ble Peripheral.
public class BlePeripheral {

    // MARK: - Variables
    
    /// Internal Device peripheral description.
    internal let peripheral: Peripheral

    // MARK: - Initializers
    
    /// Initialize from *ScannedPeripheral*.
    /// - parameter peripheral: peripheral received from scan.
    /// - returns: created BlePeripheral.
    public init(peripheral: ScannedPeripheral) {
        self.peripheral = peripheral.peripheral
    }
    
    /// Block default init.
    private init() {
        fatalError("Please use init(peripheral:) instead.")
    }
    
    // MARK: - Public methods
    
    /// Returns peripheral name.
    public func getName() -> String? {
        return peripheral.name
    }
    
    /// Returns peripheral identifier, usually as UUID string.
    public func getUuid() -> String {
        return peripheral.peripheral.identifier.uuidString
    }
    
}
