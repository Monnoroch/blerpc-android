import Foundation
import CoreBluetooth
import RxBluetoothKit
import RxSwift
import PromiseKit

/// Main class to interact with Ble Device
public class BleWorker {
    
    // MARK: - Variables
    
    /// Bluetooth Central Manager
    private let manager: CentralManager = CentralManager(queue: .main)
    
    /// Disposable which holds current device connection
    private var deviceConnection: Disposable? = nil
    
    /// Disposable which holds current device disconnection observing
    private var diconnectionDisposable: Disposable? = nil

    /// Data structure which holds key value for characteristic and it's disposable. Used to stop receiving updates from characteristics
    internal var disposableBag: [BleOperationIdentifier: Disposable?] = [:]

    /// Connected peripheral
    internal var connectedPeripheral: Peripheral?
    
    /// Queue to synchronize operations
    private let accessQueue = DispatchQueue(label: "BleWorkerQueue", attributes: .concurrent)
    
    /// Global operation id, on 64 bit systems Int64
    private var internalOperationId: Int = 0
    
    /// Check if iOS Device connected to peripheral or not
    public var isPeripheralConnected: Bool {
        get {
            return connectedPeripheral != nil
        }
    }
    
    // MARK: - Public Methods

    /// Public init method
    public init() { }
    
    /// Start scanning for peripherals
    /// - parameter complition: callback when new device was founded
    public func scanPeripherals(complition: @escaping (BleDevice) -> Void) {
        deviceConnection = manager.scanForPeripherals(withServices: nil).subscribe(onNext: { peripheral in
            complition(BleDevice.init(peripheral: peripheral))
        })
    }
    
    /// Stop scan peripherals
    public func stopScanPeripherals() {
        deviceConnection?.dispose()
    }
    
    /// Connecting to peripheral with *UUID*
    /// - parameter uuid: *UUID* of device to which connecting
    public func connectToPeripheral(uuid: String) -> Promise<Void> {
        return Promise { seal in
            self.scanPeripherals { (scannedPeripheral) in
                if scannedPeripheral.peripheral.identifier.uuidString == uuid {
                    self.stopScanPeripherals()
                    
                    self.connectToPeripheral(peripheral: scannedPeripheral).map {
                        self.startObservingDisconnection()
                        seal.fulfill(())
                    }.catch { error in
                        seal.reject(error)
                    }
                }
            }
        }
    }
    
    /// Connecting to peripheral
    /// - parameter peripheral: device to which connecting
    public func connectToPeripheral(peripheral: BleDevice) -> Promise<Void> {
        return Promise { seal in
            self.stopScanPeripherals()
            self.deviceConnection = manager.establishConnection(peripheral.peripheral).subscribe(onNext: { _ in
                self.connectedPeripheral = peripheral.peripheral
                self.startObservingDisconnection()
                seal.fulfill(())
            }, onError: { (error) in
                seal.reject(error)
            }, onCompleted: nil, onDisposed: nil)
        }
    }
    
    /// Disconnecting from peripheral
    public func disconnectFromPeripheral() {
        for (_, value) in disposableBag {
            value?.dispose()
        }
        
        diconnectionDisposable?.dispose()
        deviceConnection?.dispose()
        connectedPeripheral = nil
    }
    
    /// Get connection state
    /// - returns: connection state
    public func connectionState() -> BluetoothState {
        return manager.state
    }
    
    /// Check if current device has a service
    /// - parameter serviceUUID: *UUID* of a service
    public func isHasService(serviceUUID: String) -> Promise<Bool> {
        return Promise { seal in
            _ = self.connectedPeripheral?.discoverServices([CBUUID.init(string: serviceUUID)]).subscribe(onSuccess: { services in
                if services.count > 0 {
                    seal.fulfill(true)
                } else {
                    seal.fulfill(false)
                }
            }, onError: { error in
                seal.reject(error)
            })
        }
    }
    
    // MARK: - Internal methods
    
    /// Discovers characteristic
    /// - parameter serviceUUID: *UUID* of a service
    /// - parameter characteristicUUID: *UUID* of a characteristic
    /// - returns: characteristic
    internal func discoverCharacteristic(serviceUUID: String, characteristicUUID: String) -> Promise<Characteristic> {
        return Promise { seal in
            _ = self.connectedPeripheral?.discoverServices([CBUUID.init(string: serviceUUID)]).asObservable().flatMap { elem in
                Observable.from(elem)
            }.flatMap { elem in
                elem.discoverCharacteristics([CBUUID.init(string: characteristicUUID)])}.subscribe(onNext: { (characteristics) in
                if characteristics.count > 0 {
                    seal.fulfill(characteristics[0])
                } else {
                    let wrongCharacteristicError: NSError = NSError(domain: "ble-module.errors", code: 0, userInfo: [NSLocalizedDescriptionKey: "Cant's find characteristic"])
                    seal.reject(wrongCharacteristicError)
                }
            }, onError: { err in
                seal.reject(err)
            })
        }
    }
    
    /// Method for disconnecting from characteristic, synchronized
    /// - parameter identifier: *BleOperationIdentifier* which contains *serviceUUID* and *characteristicUUID*
    internal func disconnect(from: BleOperationIdentifier) {
        accessQueue.sync {
            let savedDisposable = self.disposableBag[from]
            savedDisposable??.dispose()
            self.disposableBag[from] = nil
        }
    }
    
    /// Adds operation to *disposableBag*, synchronized
    /// - parameter operation: ble operation
    /// - parameter disposable: disposable for this operaiton
    internal func add(operation: BleOperationIdentifier, disposable: Disposable) {
        accessQueue.sync {
            self.disposableBag[operation] = disposable
        }
    }
    
    /// Generates operation id, synchronized
    /// - returns: generated id
    internal func generateId() -> Int {
        accessQueue.sync {
            internalOperationId += 1
        }
        
        return internalOperationId
    }
    
    // MARK: - Private methods
    
    /// Method used to start detecting disconnection from connected peripheral
    private func startObservingDisconnection() {
        diconnectionDisposable = manager.observeDisconnect().subscribe(onNext: { (peripheral, disconnectReason) in
            self.disconnectFromPeripheral()
        }, onError: { (error) in
            self.disconnectFromPeripheral()
        }, onCompleted: {
            self.disconnectFromPeripheral()
        }) { }
    }
    
}
