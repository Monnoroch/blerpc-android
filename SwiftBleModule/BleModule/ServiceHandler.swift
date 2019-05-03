import Foundation
import SwiftProtobuf

/// Structure which supports service callbacks and identification by *id*
public struct ServiceHandler<P, R>: Equatable, Hashable {
    
    // MARK: - Variables
    
    /// Id of handler
    let id: Int
    
    /// Completion closure
    let completionClosure: (P) -> R
    
    /// Error closure
    let errorClosure: (Swift.Error) -> Void
    
    /// Service from where handler was created
    internal let service: AbstractService
    
    /// Unsubscribe selector
    internal let unsubscribeSelector: Selector
    
    // MARK: - Hashable Protocol support

    /// Hash value to identify *ServiceHandler*
    public var hashValue: Int {
        return id
    }
    
    /// Custom equal operator
    public static func == (lhs: ServiceHandler, rhs: ServiceHandler) -> Bool {
        return lhs.id == rhs.id
    }
    
    /// Automatically resolves unsubscribe
    public func unsubscribe() {
        if service.responds(to: unsubscribeSelector) {
            Thread.detachNewThreadSelector(unsubscribeSelector, toTarget: service, with: self)
        }
    }
    
}
