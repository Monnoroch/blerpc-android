import Foundation

/// Ble abstract service that supports injection of BleWorker.
open class BleRpcService {
    // MARK: - Variables

    /// BleWorker with which service will interact.
    public let bleServiceDriver: BleServiceDriver

    // MARK: - Initializers

    /// Initialize with *BleWorker*.
    /// - parameter bleWorker: BleWorker with connected device.
    /// - returns: service with injected BleWorker.
    public init(_ bleServiceDriver: BleServiceDriver) {
        self.bleServiceDriver = bleServiceDriver
    }

    /// Block default init.
    private init() {
        fatalError("Please use init(bleServiceDriver:) instead.")
    }
}
