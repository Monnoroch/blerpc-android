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

    // TODO(#70): remove support for connected peripherals.
    private let connectedPeripheral: Bool

    // MARK: - Initializers

    /// Please use init(peripheral:) instead.
    private init() {
        fatalError("Please use init(peripheral:) instead.")
    }

    /// Initialize with connected peripheral.
    /// - parameter device: peripheral to operate with.
    /// - returns: created *BleServiceDriver*.
    public init(peripheral: Peripheral) {
        self.peripheral = peripheral
        self.connectedPeripheral = false
    }

    // TODO(#70): remove support for connected peripherals.
    public init(peripheral: Peripheral, connected: Bool) {
        self.peripheral = peripheral
        self.connectedPeripheral = connected
    }

    // MARK: - Internal methods

    /// Call subscribe request over Ble.
    /// - parameter request: proto request encoded to Data. Must be empty message.
    /// - parameter serviceUUID: *UUID* of a service.
    /// - parameter characteristicUUID: *UUID* of a characteristic.
    /// - returns: Data as observable value.
    /// - warning: request must be empty for Read requests.
    public func subscribe(
        request data: Data,
        serviceUUID: String,
        characteristicUUID: String
    ) throws -> Observable<Data> {
        if data.count > 0 {
            return Observable.error(BleServiceDriverErrors.nonEmptyRequest)
        }
        return connectToDeviceAndDiscoverCharacteristic(
            serviceUUID: serviceUUID,
            characteristicUUID: characteristicUUID)
            .flatMap { characteristic in
                characteristic.observeValueUpdateAndSetNotification()
            }.asObservable().map { characteristic in
                guard let data = characteristic.value else {
                    throw BluetoothError.characteristicReadFailed(
                        characteristic,
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
        return connectToDeviceAndDiscoverCharacteristic(
            serviceUUID: serviceUUID,
            characteristicUUID: characteristicUUID)
            .flatMap { characteristic in
                characteristic.readValue()
            }.asSingle().map { characteristic in
                guard let data = characteristic.value else {
                    throw BluetoothError.characteristicReadFailed(
                        characteristic,
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
        return connectToDeviceAndDiscoverCharacteristic(
            serviceUUID: serviceUUID,
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
        return Observable<Peripheral?>.create { [weak self] observer -> Disposable in
            guard let `self` = self else { return Disposables.create() }
            // TODO(#70): remove support for connected peripherals.
            if self.connectedPeripheral {
                observer.onNext(self.peripheral)
            }
            observer.onNext(nil)
            return Disposables.create()
        }.flatMapLatest { [weak self] peripheral -> Observable<Peripheral> in
            guard peripheral == nil else { return .just(peripheral!) }
            guard let peripheral = self?.peripheral else { return .empty() }
            return peripheral.establishConnection()
        }
        .retry(1)
        .take(1)
        .asSingle()
    }

    /// Method which connects to device (if needed) and discover requested characteristic.
    /// - parameter serviceUUID: UUID of requested service.
    /// - parameter characteristicUUID: UUID of requested characteristic.
    /// - returns: Characteristic as observable value.
    private func connectToDeviceAndDiscoverCharacteristic(serviceUUID: String, characteristicUUID: String)
        -> Observable<Characteristic>
    {
        return getConnectedPeripheral().asObservable().flatMap { peripheral -> Observable<Characteristic> in
            peripheral.discoverServices([CBUUID(string: serviceUUID)]).asObservable().flatMap { services in
                Observable.from(services)
            }.flatMap { service in
                service.discoverCharacteristics([CBUUID(string: characteristicUUID)])
            }.flatMap { characteristics in
                Observable.from(characteristics)
            }
        }
    }
}
