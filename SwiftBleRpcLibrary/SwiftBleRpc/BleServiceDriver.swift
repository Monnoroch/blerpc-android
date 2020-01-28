import CoreBluetooth
import Foundation
import RxBluetoothKit
import RxSwift

/// Enum that describes Ble Service Driver errors.
public enum BleServiceDriverErrors: Error {
    /// Called when device returned empty response so we can not parse it as Proto object.
    case emptyResponse

    /// Called when client sends non empty request for read or subscribe methods.
    case nonEmptyRequest
}

/// Class which holds all operation with data transfer between iOS and Ble Device.
open class BleServiceDriver {
    // MARK: - Variables

    /// Connected peripheral.
    private var peripheral: Peripheral?

    /// BleServiceDriver queue which used to make thread safe read/write
    private let queue: DispatchQueue

    /// Disposable which holds current device connection.
    private var disconnectionDisposable: Disposable?

    /// Shared observer which holds establish device connection.
    private var sharedConnectedPeripheral: Observable<Peripheral>?

    // TODO(#70): remove support for connected peripherals.
    private let connectedPeripheral: Bool

    // MARK: - Initializers

    /// Block default init.
    private init() {
        fatalError("Please use init(peripheral:, queue:) instead.")
    }

    // TODO (#70): Make init(queue:) private and peripheral non optional type.
    internal init(queue: DispatchQueue) {
        self.queue = queue
        self.connectedPeripheral = false
    }

    /// Initialize with connected peripheral.
    /// - parameter device: peripheral to operate with.
    /// - parameter queue: dispatch queue used to make thread safe reqad/write.
    /// - returns: created *BleServiceDriver*.
    public init(peripheral: Peripheral, queue: DispatchQueue) {
        self.peripheral = peripheral
        self.queue = queue
        self.connectedPeripheral = false
    }

    // TODO(#70): remove support for connected peripherals.
    public init(peripheral: Peripheral, queue: DispatchQueue, connected: Bool) {
        self.peripheral = peripheral
        self.queue = queue
        self.connectedPeripheral = connected
    }

    // MARK: - Public methods

    /// Disconnecting from peripheral synchronically.
    public func disconnect() {
        queue.sync {
            disconnectionDisposable?.dispose()
            disconnectionDisposable = nil
        }
    }

    // MARK: - Internal methods

    /// Call subscribe request over Ble.
    /// - parameter request: proto request encoded to Data. Must be empty message.
    /// - parameter serviceUUID: *UUID* of a service.
    /// - parameter characteristicUUID: *UUID* of a characteristic.
    /// - returns: Data as observable value.
    /// - warning: request must be empty for Read requests.
    public func subscribe(request data: Data,
                            serviceUUID: String,
                            characteristicUUID: String) throws -> Observable<Data> {
        if data.count > 0 {
            return Observable.error(BleServiceDriverErrors.nonEmptyRequest)
        }
        return self.connectToDeviceAndDiscoverCharacteristic(serviceUUID: serviceUUID,
                                                       characteristicUUID: characteristicUUID)
            .flatMap { characteristic in
            characteristic.observeValueUpdateAndSetNotification()
        }.asObservable().map { characteristic in
            guard let data = characteristic.value else {
                throw BluetoothError.characteristicReadFailed(characteristic,
                                                               BleServiceDriverErrors.emptyResponse)
            }
            return data
        }
    }

    /// Call read request over Ble.
    /// - parameter request: proto request encoded to Data. Must be empty message.
    /// - parameter serviceUUID: *UUID* of a service.
    /// - parameter characteristicUUID: *UUID* of a characteristic.
    /// - returns: Data as observable value.
    /// - warning: request must be empty for Read requests.
    public func read(request data: Data, serviceUUID: String, characteristicUUID: String) throws -> Single<Data> {
        if data.count > 0 {
            return Single.error(BleServiceDriverErrors.nonEmptyRequest)
        }
        return self.connectToDeviceAndDiscoverCharacteristic(serviceUUID: serviceUUID,
                                                       characteristicUUID: characteristicUUID)
            .flatMap { characteristic in
            characteristic.readValue()
        }.asSingle().map { characteristic in
            guard let data = characteristic.value else {
                throw BluetoothError.characteristicReadFailed(characteristic,
                                                                             BleServiceDriverErrors.emptyResponse)
            }
            return data
        }
    }

    /// Call write request over Ble.
    /// - parameter request: proto request encoded to Data.
    /// - parameter serviceUUID: *UUID* of a service.
    /// - parameter characteristicUUID: *UUID* of a characteristic.
    /// - returns: Data as observable value.
    public func write(request: Data, serviceUUID: String, characteristicUUID: String) -> Single<Data> {
        return self.connectToDeviceAndDiscoverCharacteristic(serviceUUID: serviceUUID,
                                                       characteristicUUID: characteristicUUID)
            .flatMap { characteristic in
            characteristic.writeValue(request, type: .withResponse)
        }.asSingle().map { _ in
            return Data()
        }
    }

    // MARK: - Private methods

    /// Check current conenction state and if not connected - trying to connect to device.
    /// - returns: Peripheral as observable value.
    private func getConnectedPeripheral() -> Single<Peripheral> {
        return self.queue.sync {
            return doGetConnectedPeripheral()
        }
    }

    /// Actual device connection.
    /// - returns: Peripheral as observable value.
    private func doGetConnectedPeripheral() -> Single<Peripheral> {
        // TODO(#70): remove support for connected peripherals.
        if self.connectedPeripheral {
            return Single.just(self.peripheral!)
        }

        if let deviceConnectionObserver = self.sharedConnectedPeripheral {
            return deviceConnectionObserver.take(1).asSingle()
        }

        let observerForDeviceConnection = self.peripheral?.establishConnection().share(replay: 1)
        self.disconnectionDisposable = observerForDeviceConnection?
            .catchError({ [weak self] (error) -> Observable<Peripheral> in
                self?.disconnect()
                // TODO(#70): return the error to the caller.
                return Observable.empty()
            }).subscribe()
        self.sharedConnectedPeripheral = observerForDeviceConnection
        return doGetConnectedPeripheral()
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
