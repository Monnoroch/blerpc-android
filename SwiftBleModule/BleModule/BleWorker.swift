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
        return Promise { [weak self] seal in
            self?.scanPeripherals { (scannedPeripheral) in
                if scannedPeripheral.peripheral.identifier.uuidString == uuid {
                    self?.stopScanPeripherals()
                    
                    self?.connectToPeripheral(peripheral: scannedPeripheral).map {
                        self?.startObservingDisconnection()
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
        return Promise { [weak self] seal in
            self?.stopScanPeripherals()
            self?.deviceConnection = manager.establishConnection(peripheral.peripheral).subscribe(onNext: { _ in
                self?.connectedPeripheral = peripheral.peripheral
                self?.startObservingDisconnection()
                seal.fulfill(())
            }, onError: { (error) in
                seal.reject(error)
            }, onCompleted: nil, onDisposed: nil)
        }
    }
    
    /// Disconnecting from peripheral
    public func disconnectFromPeripheral() {
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
        return Promise { [weak self] seal in
            _ = self?.connectedPeripheral?.discoverServices([CBUUID.init(string: serviceUUID)]).subscribe(onSuccess: { services in
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
    
    /// Call subscribe request over Ble
    /// - parameter request: proto request encoded to Data
    /// - parameter serviceUUID: *UUID* of a service
    /// - parameter characteristicUUID: *UUID* of a characteristic
    /// - returns: *Observable* Data
    internal func subscribe(request: Data, serviceUUID: String, characteristicUUID: String) -> Observable<Data> {
        return Observable.create { [weak self] observer in
            var disposable: Disposable?
            disposable = self?.discoverCharacteristic(serviceUUID: serviceUUID, characteristicUUID: characteristicUUID)
            .subscribe(onNext: { characteristic in
                disposable = characteristic.observeValueUpdateAndSetNotification().subscribe({ (event) in
                    self?.completeSubscription(event: event, observer: observer)
                })
            })
            
            return Disposables.create {
                disposable?.dispose()
            }
        }
    }
    
    /// Call read request over Ble
    /// - parameter request: proto request encoded to Data
    /// - parameter serviceUUID: *UUID* of a service
    /// - parameter characteristicUUID: *UUID* of a characteristic
    /// - returns: *Observable* Data
    internal func read(request: Data, serviceUUID: String, characteristicUUID: String) -> Observable<Data> {
        return Observable.create { [weak self] observer in
            var disposable: Disposable?
            disposable = self?.discoverCharacteristic(serviceUUID: serviceUUID, characteristicUUID: characteristicUUID)
            .subscribe(onNext: { characteristic in
                disposable = characteristic.readValue().subscribe({ (event) in
                    self?.completeReadWrite(event: event, observer: observer)
                })
            })
            
            return Disposables.create {
                disposable?.dispose()
            }
        }
    }
    
    /// Call write request over Ble
    /// - parameter request: proto request encoded to Data
    /// - parameter serviceUUID: *UUID* of a service
    /// - parameter characteristicUUID: *UUID* of a characteristic
    /// - returns: *Observable* Data
    internal func write(request: Data, serviceUUID: String, characteristicUUID: String) -> Observable<Data> {
        return Observable.create { [weak self] observer in
            var disposable: Disposable?
            disposable = self?.discoverCharacteristic(serviceUUID: serviceUUID, characteristicUUID: characteristicUUID)
            .subscribe(onNext: { characteristic in
                disposable = characteristic.writeValue(request, type: .withResponse).subscribe({ (event) in
                    self?.completeReadWrite(event: event, observer: observer)
                })
            })
            
            return Disposables.create {
                disposable?.dispose()
            }
        }
    }
    
    // MARK: - Private methods
    
    /// Discovers characteristic
    /// - parameter serviceUUID: *UUID* of a service
    /// - parameter characteristicUUID: *UUID* of a characteristic
    /// - returns: characteristic
    private func discoverCharacteristic(serviceUUID: String, characteristicUUID: String) -> Observable<Characteristic> {
        return Observable.create { [weak self] observer in
            let disposable = self?.connectedPeripheral?.discoverServices([CBUUID.init(string: serviceUUID)])
                .asObservable().flatMap { services in
                    Observable.from(services)
                }.flatMap { service in
                    service.discoverCharacteristics([CBUUID.init(string: characteristicUUID)])
                }.asObservable().flatMap { characteristics in
                    Observable.from(characteristics)
                }.subscribe(onNext: { characteristic in
                    observer.onNext(characteristic)
                })
            
            return Disposables.create {
                disposable?.dispose()
            }
        }
    }
    
    /// Method called after iOS device received response from Ble device for subscribing events
    /// - parameter event: event response
    /// - parameter observer: injected observer
    private func completeSubscription(event: Event<Characteristic>, observer: AnyObserver<Data>) {
        switch event {
        case .completed:
            observer.onCompleted()
        case .error(let error):
            observer.onError(error)
        case .next(let characteristic):
            guard let data = characteristic.value else {
                let wrongDataError = NSError(domain: "blerpc.errors", code: 0, userInfo:
                    [NSLocalizedDescriptionKey: "Device returned empty response"])
                observer.onError(wrongDataError)
                return
            }
            
            observer.onNext(data)
        }
    }
    
    /// Method called after iOS device received response from Ble device for read or write events
    /// - parameter event: event response
    /// - parameter observer: injected observer
    private func completeReadWrite(event: SingleEvent<Characteristic>, observer: AnyObserver<Data>) {
        switch event {
        case .error(let error):
            observer.onError(error)
        case .success(let characteristic):
            guard let data = characteristic.value else {
                let wrongDataError = NSError(domain: "blerpc.errors", code: 0, userInfo:
                    [NSLocalizedDescriptionKey: "Device returned empty response"])
                observer.onError(wrongDataError)
                return
            }
            
            observer.onNext(data)
            observer.onCompleted()
        }
    }
    
    /// Start observing device diconnection
    private func startObservingDisconnection() {
        diconnectionDisposable = manager.observeDisconnect().subscribe(onNext: { [weak self] (peripheral, disconnectReason) in
            self?.disconnectFromPeripheral()
        }, onError: { [weak self] (error) in
            self?.disconnectFromPeripheral()
        }, onCompleted: { [weak self] in
            self?.disconnectFromPeripheral()
        }) { }
    }
    
}
