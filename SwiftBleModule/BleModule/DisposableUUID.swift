import Foundation

/// Structure describing service UUID and characteristic UUID
public struct DisposableUUID: Hashable {
    
    // MARK: - Variables
    
    /// UUID of service
    let serviceUUID: UUID
    
    /// UUID of characteristic
    let characteristicUUID: UUID
    
    // MARK: - Hashable Protocol support
    
    /// Hash value to identify *DisposableUUID*
    public var hashValue: Int {
        return "\(serviceUUID.uuidString) - \(characteristicUUID.uuidString)".hashValue
    }
    
    /// Custom equal operator
    static public func ==(lhs: DisposableUUID, rhs: DisposableUUID) -> Bool {
        return "\(lhs.serviceUUID.uuidString) - \(lhs.characteristicUUID.uuidString)".hashValue == "\(rhs.serviceUUID.uuidString) - \(rhs.characteristicUUID.uuidString)".hashValue
    }
    
}
