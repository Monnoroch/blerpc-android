import CoreBluetooth
import Foundation
import RxBluetoothKit
import RxSwift

/// Enum that describes BleWorker errors.
public enum BleWrokerErrors: Error {
    /// Called when device returned empty response so we can not parse it as Proto object.
    case emptyResponse
    
    /// Called when device returned non empty response. We do not support parsing data in write response for now.
    case nonEmptyResponse
    
    /// Called when client sends non empty request for read or subscribe methods.
    case nonEmptyRequest
    
    /// Called when device was disconnected without reason.
    case disconnectedWithoutReason
    
    /// Called when Ble finished request without response in *onNext* and we don't expect this behavior.
    case unexpectedComplete
    
    /// Called when characteristic failed read value.
    case characteristicReadFailed(characteristic: Characteristic?, Error)
    
    /// Called when characteristic failed write value.
    case characteristicWriteFailed(characteristic: Characteristic?, Error)
}

/// Class which holds all operation with data transfer between iOS and Ble Device.
public class BleWorker {
    // MARK: - Variables

    /// Connected peripheral.
    private let connectedPeripheral: Peripheral

    /// BleWorker queue which used to make thread safe read/write
    private let accessQueue: DispatchQueue

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
    }
    
    // MARK: - Internal methods

    /// Call subscribe request over Ble.
    /// - parameter request: proto request encoded to Data. Must be empty message.
    /// - parameter serviceUUID: *UUID* of a service.
    /// - parameter characteristicUUID: *UUID* of a characteristic.
    /// - returns: Data as observable value.
    /// - warning: request must be empty for Read requests.
    internal func subscribe(request data: Data, serviceUUID: String, characteristicUUID: String) -> Observable<Data> {
        if data.count > 0 {
            return Observable.error(BleWrokerErrors.nonEmptyRequest)
        }
        
        return self.connectToDeviceAndDiscoverCharacteristic(serviceUUID: serviceUUID,
                                                       characteristicUUID: characteristicUUID)
            .flatMap { characteristic in
            characteristic.observeValueUpdateAndSetNotification()
        }.asObservable().flatMap { characteristic in
            BleWorker.completeSubscription(characteristic: characteristic)
        }
    }

    /// Call read request over Ble.
    /// - parameter request: proto request encoded to Data. Must be empty message.
    /// - parameter serviceUUID: *UUID* of a service.
    /// - parameter characteristicUUID: *UUID* of a characteristic.
    /// - returns: Data as observable value.
    /// - warning: request must be empty for Read requests.
    internal func read(request data: Data, serviceUUID: String, characteristicUUID: String) -> Single<Data> {
        if data.count > 0 {
            return Single.error(BleWrokerErrors.nonEmptyRequest)
        }
        
        return self.connectToDeviceAndDiscoverCharacteristic(serviceUUID: serviceUUID,
                                                       characteristicUUID: characteristicUUID)
            .flatMap { characteristic in
            characteristic.readValue()
        }.asSingle().flatMap { characteristic in
            BleWorker.completeRead(characteristic: characteristic)
        }
    }

    /// Call write request over Ble.
    /// - parameter request: proto request encoded to Data.
    /// - parameter serviceUUID: *UUID* of a service.
    /// - parameter characteristicUUID: *UUID* of a characteristic.
    /// - returns: Data as observable value.
    internal func write(request: Data, serviceUUID: String, characteristicUUID: String) -> Single<Data> {
        return self.connectToDeviceAndDiscoverCharacteristic(serviceUUID: serviceUUID,
                                                       characteristicUUID: characteristicUUID)
            .flatMap { characteristic in
            characteristic.writeValue(request, type: .withResponse)
        }.asSingle().flatMap { characteristic in
            BleWorker.completeWrite(characteristic: characteristic)
        }
    }

    // MARK: - Private methods

    /// Check current conenction state and if not connected - trying to connect to device.
    /// - returns: Peripheral as observable value.
    private func getConnectedPeripheral() -> Single<Peripheral> {
        return self.accessQueue.sync {
            return doGetConnectedPeripheral()
        }
    }
    
    private func doGetConnectedPeripheral() -> Single<Peripheral> {
        if let deviceConnectionObserver = self.sharedObserverForDeviceConnection {
            return deviceConnectionObserver.take(1).asSingle()
        } else {
            let observerForDeviceConnection = self.connectedPeripheral.establishConnection().share(replay: 2)
            
            self.deviceConnection = observerForDeviceConnection
                .catchError({ [weak self] (error) -> Observable<Peripheral> in
                self?.doDisconnect()
                return Observable.empty()
            }).subscribe()
            
            self.sharedObserverForDeviceConnection = observerForDeviceConnection
            
            return doGetConnectedPeripheral()
        }
    }

    /// Method called after iOS device received response from Ble device for subscribing events.
    /// - parameter characteristic: characteristic that comes from request.
    private static func completeSubscription(characteristic: Characteristic) -> Observable<Data> {
        guard let data = characteristic.value else {
            return Observable.error(BleWrokerErrors.characteristicReadFailed(characteristic: characteristic,
                                                                                     BleWrokerErrors.emptyResponse))
        }
        
        return Observable.just(data)
    }

    /// Method called after iOS device received response from Ble device for read events.
    /// - parameter characteristic: characteristic that comes from request.
    private static func completeRead(characteristic: Characteristic) -> Single<Data>{
        guard let data = characteristic.value else {
            return Single.error(BleWrokerErrors.characteristicReadFailed(characteristic: characteristic,
                                                                          BleWrokerErrors.emptyResponse))
        }
        
        return Single.just(data)
    }
    
    /// Method called after iOS device received response from Ble device for write events.
    /// - parameter characteristic: characteristic that comes from request.
    private static func completeWrite(characteristic: Characteristic) -> Single<Data> {
        if let data = characteristic.value, data.count > 0  {
            return Single.error(BleWrokerErrors.characteristicWriteFailed(characteristic: characteristic,
                                                                                 BleWrokerErrors.nonEmptyResponse))
        } else {
            return Single.just(Data())
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
