import Foundation

/// Structure describing service UUID and characteristic UUID
public struct BleOperationIdentifier: Hashable {
    
    // MARK: - Variables
    
    /// UUID of service
    let serviceUUID: UUID
    
    /// UUID of characteristic
    let characteristicUUID: UUID
    
    // MARK: - Hashable Protocol support
    
    /// Hash value to identify *BleOperationIdentifier*
    public var hashValue: Int {
        return toString().hashValue
    }
    
    /// Custom equal operator
    static public func ==(lhs: BleOperationIdentifier, rhs: BleOperationIdentifier) -> Bool {
        return lhs.toString().hashValue == rhs.toString().hashValue
    }
    
    private func toString() -> String {
        return "\(serviceUUID.uuidString) - \(characteristicUUID.uuidString)"
    }
    
}
