import CoreBluetooth
import Foundation
import RxBluetoothKit
import RxSwift

/// Enum that describes BleWorker errors.
public enum BleWrokerErrors: Error {
    /// Called when self is nil (destroyed).
    case destroyed
    
    /// Called when device returned empty response so we can not parse it as Proto object.
    case emptyResponse
    
    /// Called when device was disconnected without reason.
    case disconnectedWithoutReason
}

/// Class which holds all operation with data transfer between iOS and Ble Device.
public class BleWorker {
    // MARK: - Variables

    /// Bluetooth Central Manager.
    private let manager: CentralManager

    /// Connected peripheral.
    private let connectedPeripheral: Peripheral

    /// BleWorker queue which used to make thread safe read/write
    private let accessQueue: DispatchQueue

    /// Max count for retrying connection of Ble device

    /// Disposable which holds current device connection.
    private var deviceConnection: Disposable?

    /// Shared observer which holds establish device connection.
    private var sharedObserverForDeviceConnection: Observable<Peripheral>?
    
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

    /// Disconnecting from peripheral synchronically.
    public func disconnect() {
        accessQueue.sync {
            doDisconnect()
        }
    }

    /// Actual disconnect from device and cleanup.
    private func doDisconnect() {
        deviceConnection?.dispose()
        sharedObserverForDeviceConnection = nil
    }
    
    // MARK: - Internal methods

    /// Call subscribe request over Ble.
    /// - parameter request: proto request encoded to Data.
    /// - parameter serviceUUID: *UUID* of a service.
    /// - parameter characteristicUUID: *UUID* of a characteristic.
    /// - returns: Data as observable value.
    internal func subscribe(request _: Data, serviceUUID: String, characteristicUUID: String) -> Observable<Data> {
        return Observable.create { [weak self] observer in
            guard let self = self else {
                observer.onError(BleWrokerErrors.destroyed)
                return Disposables.create()
            }
            
            return self.connectToDeviceAndDiscoverCharacteristic(serviceUUID: serviceUUID,
                                                           characteristicUUID: characteristicUUID)
                .flatMap { characteristic in
                characteristic.observeValueUpdateAndSetNotification()
            }.subscribe { event in
                BleWorker.completeSubscription(event: event, observer: observer)
            }
        }
    }

    /// Call read request over Ble.
    /// - parameter request: proto request encoded to Data.
    /// - parameter serviceUUID: *UUID* of a service.
    /// - parameter characteristicUUID: *UUID* of a characteristic.
    /// - returns: Data as observable value.
    internal func read(request _: Data, serviceUUID: String, characteristicUUID: String) -> Single<Data> {
        return Single.create { [weak self] observer in
            guard let self = self else {
                observer(.error(BleWrokerErrors.destroyed))
                return Disposables.create()
            }
            
            return self.connectToDeviceAndDiscoverCharacteristic(serviceUUID: serviceUUID,
                                                           characteristicUUID: characteristicUUID)
                .flatMap { characteristic in
                characteristic.readValue()
            }.subscribe { event in
                BleWorker.completeReadWrite(event: event, observer: observer)
            }
        }
    }

    /// Call write request over Ble.
    /// - parameter request: proto request encoded to Data.
    /// - parameter serviceUUID: *UUID* of a service.
    /// - parameter characteristicUUID: *UUID* of a characteristic.
    /// - returns: Data as observable value.
    internal func write(request: Data, serviceUUID: String, characteristicUUID: String) -> Single<Data> {
        return Single.create { [weak self] observer in
            guard let self = self else {
                observer(.error(BleWrokerErrors.destroyed))
                return Disposables.create()
            }
            
            return self.connectToDeviceAndDiscoverCharacteristic(serviceUUID: serviceUUID,
                                                           characteristicUUID: characteristicUUID)
                .flatMap { characteristic in
                characteristic.writeValue(request, type: .withResponse)
            }.subscribe { event in
                BleWorker.completeReadWrite(event: event, observer: observer)
            }
        }
    }

    // MARK: - Private methods

    /// Check current conenction state and if not connected - trying to connect to device.
    /// - returns: Peripheral as observable value.
    private func getConnectedPeripheral() -> Single<Peripheral> {
        return Single.create { [weak self] observer in
            self?.accessQueue.sync {
                guard let self = self else {
                    observer(.error(BleWrokerErrors.destroyed))
                    return Disposables.create()
                }
                
                if self.connectedPeripheral.isConnected {
                    observer(.success(self.connectedPeripheral))
                    return Disposables.create()
                } else if let deviceConnectionObserver = self.sharedObserverForDeviceConnection {
                    return deviceConnectionObserver.subscribe(onNext: { peripheral in
                        observer(.success(peripheral))
                    }, onError: { error in
                        observer(.error(error))
                    })
                } else {
                    self.sharedObserverForDeviceConnection = self.manager.establishConnection(self.connectedPeripheral)
                        .share()
                    self.deviceConnection = self.sharedObserverForDeviceConnection?.subscribe(onNext: { peripheral in
                        observer(.success(peripheral))
                    }, onError: { error in
                        observer(.error(error))
                    })
                    
                    return Disposables.create()
                }
            } ?? Disposables.create()
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
                observer.onError(BleWrokerErrors.emptyResponse)
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
                observer(.error(BleWrokerErrors.emptyResponse))
                return
            }

            observer(.success(data))
        case .completed:
            break
        }
    }
    
    /// Method which connects to device (if needed) and discover requested characteristic.
    /// - parameter serviceUUID: UUID of requested service.
    /// - parameter characteristicUUID: UUID of requested characteristic.
    /// - returns: Characteristic as observable value.
    private func connectToDeviceAndDiscoverCharacteristic(serviceUUID: String, characteristicUUID: String)
        -> Observable<Characteristic> {
        return self.getConnectedPeripheral().asObservable().flatMap { peripheral -> Observable<Characteristic> in
            peripheral.discoverServices([CBUUID(string: serviceUUID)]).asObservable().flatMap { services in
                Observable.from(services)
            }.flatMap { service in
                service.discoverCharacteristics([CBUUID(string: characteristicUUID)])
            }.asObservable().flatMap { characteristics in
                Observable.from(characteristics)
            }
        }
    }
}
