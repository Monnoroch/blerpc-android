import CoreBluetooth
import Foundation
import RxBluetoothKit
import RxSwift
import RxSwiftExt

/// Class which holds all operation with data transfer between iOS and Ble Device.
public class BleWorker {
    // MARK: - Variables
    
    /// Bluetooth Central Manager.
    private let manager: CentralManager
    
    /// Connected peripheral.
    private let connectedPeripheral: Peripheral
    
    /// BleWorker queue which used to make thread safe read/write
    private let accessQueue: DispatchQueue
    
    /// Behavior of retry policy. Also used to wait until connection will be established from another request
    private static let retryBehavior: RepeatBehavior = RepeatBehavior.exponentialDelayed(maxCount: 3, initial: 1.0, multiplier: 1.0)
    
    /// Max count for retrying connection of Ble device
    
    /// Disposable which holds current device connection.
    private var deviceConnection: Disposable?
    
    /// Disposable which holds current device disconnection observing.
    private var diconnectionDisposable: Disposable?
    
    // MARK: - Initializers
    
    /// Block default init.
    private init() {
        fatalError("Please use init(peripheral:, accessQueue:) instead.")
    }
    
    /// Initialize with connected peripheral.
    /// - parameter device: peripheral to operate with.
    /// - parameter accessQueue: dispatch queue used to make thread safe reqad/write.
    /// - returns: created *BleWorker*.
    public init(peripheral: BlePeripheral, accessQueue: DispatchQueue) {
        connectedPeripheral = peripheral.peripheral
        manager = peripheral.peripheral.manager
        self.accessQueue = accessQueue
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
    internal func subscribe(request _: Data, serviceUUID: String, characteristicUUID: String) -> Observable<Data> {
        return Observable.create { [weak self] observer in
            self?.connectIfNeeded().asObservable().retry(BleWorker.retryBehavior)
            .flatMap {
                self?.discoverCharacteristic(serviceUUID: serviceUUID, characteristicUUID: characteristicUUID) ?? Observable.empty()
            }.flatMap { characteristic in
                characteristic.observeValueUpdateAndSetNotification()
            }.subscribe { event in
                BleWorker.completeSubscription(event: event, observer: observer)
            } ?? Disposables.create()
        }
    }
    
    /// Call read request over Ble.
    /// - parameter request: proto request encoded to Data.
    /// - parameter serviceUUID: *UUID* of a service.
    /// - parameter characteristicUUID: *UUID* of a characteristic.
    /// - returns: *Observable* Data.
    internal func read(request _: Data, serviceUUID: String, characteristicUUID: String) -> Single<Data> {
        return Single.create { [weak self] observer in
            self?.connectIfNeeded().asObservable().retry(BleWorker.retryBehavior)
            .flatMap {
                self?.discoverCharacteristic(serviceUUID: serviceUUID,characteristicUUID: characteristicUUID) ?? Observable.empty()
            }.flatMap { characteristic in
                characteristic.readValue()
            }.subscribe { event in
                BleWorker.completeReadWrite(event: event, observer: observer)
            } ?? Disposables.create()
        }
    }
    
    /// Call write request over Ble.
    /// - parameter request: proto request encoded to Data.
    /// - parameter serviceUUID: *UUID* of a service.
    /// - parameter characteristicUUID: *UUID* of a characteristic.
    /// - returns: *Observable* Data.
    internal func write(request: Data, serviceUUID: String, characteristicUUID: String) -> Single<Data> {
        return Single.create { [weak self] observer in
            self?.connectIfNeeded().asObservable().retry(BleWorker.retryBehavior).flatMap {
                self?.discoverCharacteristic(serviceUUID: serviceUUID, characteristicUUID: characteristicUUID) ?? Observable.empty()
            }.flatMap { characteristic in
                characteristic.writeValue(request, type: .withResponse)
            }.subscribe { event in
                BleWorker.completeReadWrite(event: event, observer: observer)
            } ?? Disposables.create()
        }
    }
    
    // MARK: - Private methods
    
    /// Connecting to peripheral and automatically starting observing disconnection state.
    /// - warning: If Ble device disconnected during ble operation - BleWorker will automatically call *disconnect* method and send error in current operation
    private func connectToPeripheral() -> PublishSubject<Void> {
        let subject = PublishSubject<Void>()
        self.startObservingDisconnection(handlerSubject: subject)

        deviceConnection = manager.establishConnection(connectedPeripheral)
        .subscribe(onNext: { _ in
            subject.onNext(())
            subject.onCompleted()
        }, onError: { error in
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
                    return self?.connectToPeripheral().subscribe(onNext: { _ in
                        observer(.success(()))
                    }, onError: { error in
                        observer(.error(error))
                    }) ?? Disposables.create()
                }
            } ?? Disposables.create()
        }
    }
    
    /// Discovers characteristic for selected service UUID and characteristic UUID.
    /// - parameter serviceUUID: *UUID* of a service.
    /// - parameter characteristicUUID: *UUID* of a characteristic.
    /// - returns: characteristic.
    private func discoverCharacteristic(serviceUUID: String, characteristicUUID: String) -> Observable<Characteristic> {
        return connectedPeripheral.discoverServices([CBUUID(string: serviceUUID)])
        .asObservable().flatMap { services in
            Observable.from(services)
        }.flatMap { service in
            service.discoverCharacteristics([CBUUID(string: characteristicUUID)])
        }.asObservable().flatMap { characteristics in
            Observable.from(characteristics)
        }
    }
    
    /// Method called after iOS device received response from Ble device for subscribing events.
    /// - parameter event: event response.
    /// - parameter observer: injected observer.
    private static func completeSubscription(event: Event<Characteristic>, observer: AnyObserver<Data>) {
        switch event {
        case .completed:
            observer.onCompleted()
        case let .error(error):
            observer.onError(error)
        case let .next(characteristic):
            guard let data = characteristic.value else {
                let wrongDataError = NSError(
                    domain: "blerpc.errors",
                    code: 0,
                    userInfo:
                    [NSLocalizedDescriptionKey: "Device returned empty response"]
                )
                observer.onError(wrongDataError)
                return
            }
            
            observer.onNext(data)
        }
    }
    
    /// Method called after iOS device received response from Ble device for read or write events.
    /// - parameter event: event response.
    /// - parameter observer: injected observer.
    private static func completeReadWrite(event: Event<Characteristic>, observer: (SingleEvent<Data>) -> Void) {
        switch event {
        case let .error(error):
            observer(.error(error))
        case let .next(characteristic):
            guard let data = characteristic.value else {
                let wrongDataError = NSError(
                    domain: "blerpc.errors",
                    code: 0,
                    userInfo:
                    [NSLocalizedDescriptionKey: "Device returned empty response"]
                )
                observer(.error(wrongDataError))
                return
            }
            
            observer(.success(data))
        case .completed:
            break
        }
    }
    
    /// Start observing device diconnection. If iOS sends disconnect event - automatically calling *disconnect* method to cleanup current connection states.
    private func startObservingDisconnection(handlerSubject: PublishSubject<Void>) {
        diconnectionDisposable = manager.observeDisconnect()
        .subscribe(onNext: { [weak self] _, disconnectReason in
            if let reason = disconnectReason {
                handlerSubject.onError(reason)
            }
            self?.disconnect()
        },onError: { [weak self] error in
            handlerSubject.onError(error)
            self?.disconnect()
        }, onCompleted: { [weak self]  in
            self?.disconnect()
        })
    }
}
