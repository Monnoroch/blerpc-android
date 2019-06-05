import Foundation
import CoreBluetooth
import RxBluetoothKit
import RxSwift

/// Class which holds all operation with data transfer between iOS and Ble Device.
public class BleWorker {
    
    // MARK: - Variables
    
    /// Bluetooth Central Manager.
    private let manager: CentralManager
    
    /// Disposable which holds current device connection.
    private var deviceConnection: Disposable? = nil
    
    /// Disposable which holds current device disconnection observing.
    private var diconnectionDisposable: Disposable? = nil
    
    /// Connected peripheral.
    private var connectedPeripheral: Peripheral
    
    /// BleWorker queue which used to make thread safe read/write
    private let accessQueue: DispatchQueue = DispatchQueue.init(label: "bleWorkerQueue")
    
    /// Error sended when ble device returned empty response
    private let wrongDataError = NSError(domain: "blerpc.errors", code: 0, userInfo:
        [NSLocalizedDescriptionKey: "Device returned empty response"])
    
    // MARK: - Initializers
    
    /// Block default init.
    private init() {
        fatalError("Please use init(peripheral:centralManager:) instead.")
    }
    
    /// Initialize with connected peripheral.
    /// - parameter device: peripheral to operate with.
    /// - returns: created *BleWorker*.
    public init(peripheral: BlePeripheral) {
        connectedPeripheral = peripheral.peripheral
        manager = peripheral.peripheral.manager
    }
    
    // MARK: - Public methods
    
    /// Disconnecting from peripheral.
    public func disconnect() {
        diconnectionDisposable?.dispose()
        deviceConnection?.dispose()
    }
    
    // MARK: - Internal methods
    
    /// Call subscribe request over Ble.
    /// - parameter request: proto request encoded to Data.
    /// - parameter serviceUUID: *UUID* of a service.
    /// - parameter characteristicUUID: *UUID* of a characteristic.
    /// - returns: *Observable* Data.
    internal func subscribe(request: Data, serviceUUID: String, characteristicUUID: String) -> Observable<Data> {
        return Observable.create { [weak self] observer in
            return self?.connectIfNeeded().asObservable()
            .flatMap { _ -> Observable<Characteristic> in
                guard let `self` = self else {
                    return Observable.empty()
                }
                return self.discoverCharacteristic(serviceUUID: serviceUUID, characteristicUUID: characteristicUUID).asObservable()
            }.flatMap { characteristic in
                characteristic.observeValueUpdateAndSetNotification()
            }.subscribe({ (event) in
                self?.completeSubscription(event: event, observer: observer)
            }) ?? Disposables.create()
        }
    }
    
    /// Call read request over Ble.
    /// - parameter request: proto request encoded to Data.
    /// - parameter serviceUUID: *UUID* of a service.
    /// - parameter characteristicUUID: *UUID* of a characteristic.
    /// - returns: *Observable* Data.
    internal func read(request: Data, serviceUUID: String, characteristicUUID: String) -> Single<Data> {
        return Single.create { [weak self] observer in
            return self?.connectIfNeeded().asObservable()
            .flatMap { _ -> Observable<Characteristic> in
                guard let `self` = self else {
                    return Observable.empty()
                }
                return self.discoverCharacteristic(serviceUUID: serviceUUID, characteristicUUID: characteristicUUID).asObservable()
            }.flatMap { characteristic in
                characteristic.readValue()
            }.subscribe({ (event) in
                self?.completeReadWrite(event: event, observer: observer)
            }) ?? Disposables.create()
        }
    }
    
    /// Call write request over Ble.
    /// - parameter request: proto request encoded to Data.
    /// - parameter serviceUUID: *UUID* of a service.
    /// - parameter characteristicUUID: *UUID* of a characteristic.
    /// - returns: *Observable* Data.
    internal func write(request: Data, serviceUUID: String, characteristicUUID: String) -> Single<Data> {
        return Single.create { [weak self] observer in
            return self?.connectIfNeeded().asObservable()
            .flatMap { _ -> Observable<Characteristic> in
                guard let `self` = self else {
                    return Observable.empty()
                }
                return self.discoverCharacteristic(serviceUUID: serviceUUID, characteristicUUID: characteristicUUID).asObservable()
            }.flatMap { characteristic in
                characteristic.writeValue(request, type: .withResponse)
            }.subscribe({ (event) in
                self?.completeReadWrite(event: event, observer: observer)
            }) ?? Disposables.create()
        }
    }
    
    // MARK: - Private methods
    
    /// Connecting to peripheral.
    /// - warning: If Ble device disconnected during ble operation - BleWorker will automatically call *disconnect* method and send error in current operation
    // TODO (68): Add tests to be sure that after unexpected disconnect worker returns error to observer
    private func connectToPeripheral() -> PublishSubject<Void> {
        let subject = PublishSubject<Void>()
        
        self.deviceConnection = self.manager.establishConnection(self.connectedPeripheral)
        .subscribe(onNext: { [weak self] _ in
            self?.startObservingDisconnection()
            subject.onNext(())
            subject.onCompleted()
        }, onError: { (error) in
            subject.onError(error)
        })
        
        return subject
    }
    
    /// Check current conenction state and if not connected - trying to connect to device.
    private func connectIfNeeded() -> Single<Void> {
        return Single.create { [weak self] observer in
            self?.accessQueue.sync {
                if let isConnected = self?.connectedPeripheral.isConnected, isConnected == true {
                    observer(.success(()))
                    return Disposables.create()
                } else {
                    return self?.connectToPeripheral().subscribe(onNext: { (Void) in
                        observer(.success(()))
                    }, onError: { (error) in
                        observer(.error(error))
                    }) ?? Disposables.create()
                }
            } ?? Disposables.create()
        }
    }
    
    /// Discovers characteristic.
    /// - parameter serviceUUID: *UUID* of a service.
    /// - parameter characteristicUUID: *UUID* of a characteristic.
    /// - returns: characteristic.
    private func discoverCharacteristic(serviceUUID: String, characteristicUUID: String) -> Single<Characteristic> {
        return Single.create { [weak self] observer in
            return self?.connectedPeripheral.discoverServices([CBUUID.init(string: serviceUUID)])
            .asObservable().flatMap { services in
                Observable.from(services)
            }.flatMap { service in
                service.discoverCharacteristics([CBUUID.init(string: characteristicUUID)])
            }.asObservable().flatMap { characteristics in
                Observable.from(characteristics)
            }.subscribe(onNext: { characteristic in
                observer(.success(characteristic))
            }) ?? Disposables.create()
        }
    }
    
    /// Method called after iOS device received response from Ble device for subscribing events.
    /// - parameter event: event response.
    /// - parameter observer: injected observer.
    private func completeSubscription(event: Event<Characteristic>, observer: AnyObserver<Data>) {
        switch event {
        case .completed:
            observer.onCompleted()
        case .error(let error):
            observer.onError(error)
        case .next(let characteristic):
            guard let data = characteristic.value else {
                observer.onError(wrongDataError)
                return
            }
            
            observer.onNext(data)
        }
    }
    
    /// Method called after iOS device received response from Ble device for read or write events.
    /// - parameter event: event response.
    /// - parameter observer: injected observer.
    private func completeReadWrite(event: Event<Characteristic>, observer: (SingleEvent<Data>) -> Void) {
        switch event {
        case .error(let error):
            observer(.error(error))
        case .next(let characteristic):
            guard let data = characteristic.value else {
                observer(.error(wrongDataError))
                return
            }
            
            observer(.success(data))
        case .completed:
            break
        }
    }
    
    /// Start observing device diconnection.
    private func startObservingDisconnection() {
        diconnectionDisposable = manager.observeDisconnect()
        .subscribe(onNext: { [weak self] (peripheral, disconnectReason) in
                self?.disconnect()
            }, onError: { [weak self] (error) in
                self?.disconnect()
            }, onCompleted: { [weak self] in
                self?.disconnect()
        })
    }
    
}
