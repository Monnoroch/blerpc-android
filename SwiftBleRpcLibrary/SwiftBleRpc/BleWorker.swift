import Foundation
import CoreBluetooth
import RxBluetoothKit
import RxSwift
import RxSwiftExt

/// Class which holds all operation with data transfer between iOS and Ble Device
public class BleWorker {
    
    // MARK: - Variables
    
    /// Check if iOS Device connected to peripheral or not
    public var isPeripheralConnected: Bool {
        get {
            return connectedPeripheral.isConnected
        }
    }
    
    /// Bluetooth Central Manager
    private let manager: CentralManager
    
    /// Disposable which holds current device connection
    private var deviceConnection: Disposable? = nil
    
    /// Disposable which holds current device disconnection observing
    private var diconnectionDisposable: Disposable? = nil
    
    /// Connected peripheral
    private var connectedPeripheral: Peripheral
    
    // MARK: - Initializers
    
    /// Block default init
    private init() {
        fatalError("Please use init(peripheral:centralManager:) instead.")
    }
    
    /// Initialize with connected peripheral
    /// - parameter device: peripheral to operate with
    /// - returns: created *BleWorker*
    public init(peripheral: BlePeripheral) {
        connectedPeripheral = peripheral.peripheral
        manager = peripheral.peripheral.manager
    }
    
    // MARK: - Public methods
    
    /// Check if current device has a service
    /// - parameter serviceUUID: *UUID* of a service
    public func isHasService(serviceUUID: String) -> Single<Bool> {
        return Single.create { [weak self] observer in
            _ = self?.connectIfNeeded().asObservable().retry(.exponentialDelayed(maxCount: 3, initial: 1.0, multiplier: 1.0)).subscribe(onNext: { (Void) in
                _ = self?.connectedPeripheral.discoverServices([CBUUID.init(string: serviceUUID)]).subscribe(onSuccess: { services in
                    if services.count > 0 {
                        observer(.success((true)))
                    } else {
                        observer(.success((false)))
                    }
                }, onError: { error in
                    observer(.error(error))
                })
            }, onError: { (error) in
                observer(.error(error))
            })
            
            return Disposables.create()
        }
    }
    
    /// Disconnecting from peripheral
    public func disconnectFromPeripheral() {
        diconnectionDisposable?.dispose()
        deviceConnection?.dispose()
    }
    
    /// Get connection state
    /// - returns: connection state
    public func connectionState() -> BluetoothState {
        return manager.state
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
            disposable = self?.connectIfNeeded().asObservable().retry(.exponentialDelayed(maxCount: 3, initial: 1.0, multiplier: 1.0)).subscribe(onNext: { (Void) in
                disposable = self?.discoverCharacteristic(serviceUUID: serviceUUID, characteristicUUID: characteristicUUID).subscribe(onSuccess: { (characteristic) in
                    disposable = characteristic.observeValueUpdateAndSetNotification().subscribe({ (event) in
                        self?.completeSubscription(event: event, observer: observer)
                    })
                }, onError: { (error) in
                    observer.onError(error)
                })
            }, onError: { (error) in
                observer.onError(error)
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
    internal func read(request: Data, serviceUUID: String, characteristicUUID: String) -> Single<Data> {
        return Single.create { [weak self] observer in
            var disposable: Disposable?
            disposable = self?.connectIfNeeded().asObservable().retry(.exponentialDelayed(maxCount: 3, initial: 1.0, multiplier: 1.0)).subscribe(onNext: { (Void) in
                disposable = self?.discoverCharacteristic(serviceUUID: serviceUUID, characteristicUUID: characteristicUUID).subscribe(onSuccess: { (characteristic) in
                    disposable = characteristic.readValue().subscribe({ (event) in
                        self?.completeReadWrite(event: event, observer: observer)
                    })
                }, onError: { (error) in
                    observer(.error(error))
                })
            }, onError: { (error) in
                observer(.error(error))
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
    internal func write(request: Data, serviceUUID: String, characteristicUUID: String) -> Single<Data> {
        return Single.create { [weak self] observer in
            var disposable: Disposable?
            disposable = self?.connectIfNeeded().asObservable().retry(.exponentialDelayed(maxCount: 3, initial: 1.0, multiplier: 1.0)).subscribe(onNext: { (Void) in
                disposable = self?.discoverCharacteristic(serviceUUID: serviceUUID, characteristicUUID: characteristicUUID)
                .subscribe(onSuccess: { (characteristic) in
                    disposable = characteristic.writeValue(request, type: .withResponse).subscribe({ (event) in
                        self?.completeReadWrite(event: event, observer: observer)
                    })
                }, onError: { (error) in
                    observer(.error(error))
                })
            }, onError: { error in
                observer(.error(error))
            })
            
            return Disposables.create {
                disposable?.dispose()
            }
        }
    }
    
    // MARK: - Private methods
    
    /// Connecting to peripheral
    private func connectToPeripheral() -> PublishSubject<Void> {
        let subject = PublishSubject<Void>()
        
        self.deviceConnection = self.manager.establishConnection(self.connectedPeripheral).subscribe(onNext: { [weak self] _ in
            self?.startObservingDisconnection()
            subject.onNext(())
            subject.onCompleted()
            }, onError: { (error) in
                subject.onError(error)
        }, onCompleted: nil, onDisposed: nil)
        
        return subject
    }
    
    /// Check current conenction state and if not connected - trying to connect to device
    private func connectIfNeeded() -> Single<Void> {
        return Single.create { [weak self] observer in
            if let isConnected = self?.isPeripheralConnected, isConnected == true {
                observer(.success(()))
                return Disposables.create()
            } else {
                let disposable = self?.connectToPeripheral().subscribe(onNext: { (Void) in
                    observer(.success(()))
                }, onError: { (error) in
                    observer(.error(error))
                }, onCompleted: nil, onDisposed: nil)
                
                return Disposables.create {
                    disposable?.dispose()
                }
            }
        }
    }
    
    /// Discovers characteristic
    /// - parameter serviceUUID: *UUID* of a service
    /// - parameter characteristicUUID: *UUID* of a characteristic
    /// - returns: characteristic
    private func discoverCharacteristic(serviceUUID: String, characteristicUUID: String) -> Single<Characteristic> {
        return Single.create { [weak self] observer in
            let disposable = self?.connectedPeripheral.discoverServices([CBUUID.init(string: serviceUUID)])
                .asObservable().flatMap { services in
                    Observable.from(services)
                }.flatMap { service in
                    service.discoverCharacteristics([CBUUID.init(string: characteristicUUID)])
                }.asObservable().flatMap { characteristics in
                    Observable.from(characteristics)
                }.subscribe(onNext: { characteristic in
                    observer(.success(characteristic))
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
    private func completeReadWrite(event: SingleEvent<Characteristic>, observer: (SingleEvent<Data>) -> Void) {
        switch event {
        case .error(let error):
            observer(.error(error))
        case .success(let characteristic):
            guard let data = characteristic.value else {
                let wrongDataError = NSError(domain: "blerpc.errors", code: 0, userInfo:
                    [NSLocalizedDescriptionKey: "Device returned empty response"])
                observer(.error(wrongDataError))
                return
            }
            
            
            observer(.success(data))
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
