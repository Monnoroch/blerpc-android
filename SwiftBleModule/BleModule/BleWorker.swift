import Foundation
import CoreBluetooth
import RxBluetoothKit
import RxSwift
import PromiseKit

/// Main class to interact with BLE Device
public class BleWorker {
    
    // MARK: - Variables
    
    /// Bluetooth Central Manager
    internal let manager: CentralManager = CentralManager(queue: .main)
    
    /// Disposable which holds current device connection
    internal var disposable: Disposable? = nil
    
    /// Disposable which holds current device disconnection observing
    private var diconnectionDisposable: Disposable? = nil

    /// Data structure which holds key value for characteristic and it's disposable. Used to stop receiving updates from characteristics
    internal var disposableBag: [DisposableUUID: Disposable?] = [:]
    
    /// Error for wrong characteristic connection
    internal let wrongCharacteristicError: NSError = NSError(domain: "AURA-BLE-Module.errors", code: 0, userInfo: [NSLocalizedDescriptionKey: "To use this functionality please update AURA Band firmware and try again"])
    internal let wrongDataError = NSError(domain: "AURA-BLE-Module.errors", code: 0, userInfo:
        [NSLocalizedDescriptionKey: "AURA Device returned empty response"])

    /// Connected peripheral
    internal var connectedPeripheral: Peripheral?
    
    /// Check if iOS Device connected to peripheral or not
    public var isConnected: Bool {
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
        disposable = manager.scanForPeripherals(withServices: nil).subscribe(onNext: { peripheral in
            if self.isAuraDevice(deviceName: peripheral.peripheral.name) {
                complition(BleDevice.init(peripheral: peripheral))
            }
        })
    }
    
    /// Stop scan peripherals
    public func stopScanPeripherals() {
        disposable?.dispose()
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
            self.disposable = manager.establishConnection(peripheral.peripheral).subscribe(onNext: { _ in
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
        disposable?.dispose()
        connectedPeripheral = nil
    }
    
    /// Get connection state
    /// - returns: connection state
    public func connectionState() -> BluetoothState {
        return manager.state
    }
    
    /// Check if current device has a service
    /// - parameter serviceUUID: *UUID* of a service
    public func isHasService(serviceUUID: String) -> Promise<Void> {
        return Promise { seal in
            _ = self.connectedPeripheral?.discoverServices([CBUUID.init(string: serviceUUID)]).subscribe(onSuccess: { services in
                if services.count > 0 {
                    seal.fulfill(())
                } else {
                    seal.reject(self.wrongCharacteristicError)
                }
            }, onError: { (error) in
                seal.reject(self.wrongCharacteristicError)
            })
        }
    }
    
    /// Discovers characteristic
    /// - parameter serviceUUID: *UUID* of a service
    /// - parameter characteristicUUID: *UUID* of a characteristic
    /// - returns: characteristic
    public func discoverCharacteristic(serviceUUID: String, characteristicUUID: String) -> Promise<Characteristic> {
        return Promise { seal in
            _ = self.connectedPeripheral?.discoverServices([CBUUID.init(string: serviceUUID)]).asObservable()
                .flatMap {
                    Observable.from($0)
                }
                .flatMap { $0.discoverCharacteristics([CBUUID.init(string: characteristicUUID)])}.subscribe(onNext: { (characteristics) in
                    if characteristics.count > 0 {
                        seal.fulfill(characteristics[0])
                    } else {
                        seal.reject(self.wrongCharacteristicError)
                    }
                }, onError: { err in
                    seal.reject(err)
                })
        }
    }
    
    /// Method for disconnecting from characteristic. Used to stop receiving updates from characteristics
    /// - parameter serviceUUID: *UUID* of a service
    /// - parameter characteristicUUID: *UUID* of a characteristic
    internal func disconnectFrom(serviceUUID: String, characteristicUUID: String) {
        let disposableId = DisposableUUID.init(serviceUUID: UUID.init(uuidString: serviceUUID)!, characteristicUUID: UUID.init(uuidString: characteristicUUID)!)
        let savedDisposable = self.disposableBag[disposableId]
        savedDisposable??.dispose()
        self.disposableBag[disposableId] = nil
    }
    
    /// Method for disconnecting from characteristic. Based on *DisposableUUID*
    /// - parameter disposableUUID: *DisposableUUID* which contains *serviceUUID* and *characteristicUUID*
    internal func disconnectFrom(disposableUUID: DisposableUUID) {
        let savedDisposable = self.disposableBag[disposableUUID]
        savedDisposable??.dispose()
        self.disposableBag[disposableUUID] = nil
    }
    
    /// Stop scan peripherals
    /// - parameter deviceName: *optional* device name
    /// - returns: if *deviceName* has *aura* in name
    internal func isAuraDevice(deviceName: String?) -> Bool {
        if let deviceNameG = deviceName {
            if deviceNameG.lowercased().contains("aura") {
                return true
            }
        }
        
        return false
    }
    
    // MARK: - Private methods
    
    /// Method used to start detecting disconnection from connected peripheral
    private func startObservingDisconnection() {
        diconnectionDisposable = manager.observeDisconnect().subscribe(onNext: { (peripheral, disconnectReason) in
            print("DISCONNECT REASON = \(disconnectReason)")
            self.disconnectFromPeripheral()
        }, onError: { (error) in
            self.disconnectFromPeripheral()
        }, onCompleted: {
            self.disconnectFromPeripheral()
        }) { }
    }
    
}
