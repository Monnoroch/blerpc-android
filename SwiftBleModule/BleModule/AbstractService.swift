import Foundation

/// Abstract service calls to support injection of BleWorker
public class AbstractService {
    
    // MARK: - Variables
    
    /// BleWorker with which service will interact
    internal let bleWorker: BleWorker
    
    // MARK: - Initializers
    
    /// Initializer
    /// - parameter bleWorker: BleWorker with connected device
    /// - returns: service with injected BleWorker
    public init (_ bleWorker: BleWorker) {
        self.bleWorker = bleWorker
    }
    
}
