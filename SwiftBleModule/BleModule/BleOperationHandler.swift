import Foundation
import SwiftProtobuf

/// Structure which supports service method cancelation and identification
public struct BleOperationHandler: Equatable, Hashable {
    
    // MARK: - Variables
    
    /// Id of handler
    let id: Int
    
    /// Service from where handler was created
    internal let service: BleAbstractService
    
    /// Cencel selector
    internal let selector: Selector
    
    // MARK: - Hashable Protocol support

    /// Hash value to identify *BleOperationHandler*
    public var hashValue: Int {
        return id
    }
    
    /// Custom equal operator
    public static func == (lhs: BleOperationHandler, rhs: BleOperationHandler) -> Bool {
        return lhs.id == rhs.id
    }
    
    /// Automatically resolves unsubscribe
    public func cancel() {
        if service.responds(to: selector) {
            Thread.detachNewThreadSelector(selector, toTarget: service, with: self)
        }
    }
    
}
