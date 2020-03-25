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

    /// Event for disconnect all subscription.
    private var disconnectAll = PublishSubject<Void>()
    
    /// Lock for establish connection.
    private let lock = NSLock()

    /// DisposeBag for establish connection.
    private var disposeBag = DisposeBag()

    /// Peripheral event for connection.
    private let peripheralEvent: BehaviorSubject<Peripheral>
    // MARK: - Initializers

    /// Please use init(peripheral:) instead.
    private init() {
        fatalError("Please use init(peripheral:) instead.")
    }

    /// Initialize with a connected peripheral.
    /// - parameter device: peripheral to operate with.
    /// - returns: created *BleServiceDriver*.
    public init(peripheral: Peripheral) {
        self.peripheral = peripheral
        self.peripheralEvent = BehaviorSubject<Peripheral>(value: peripheral)
    }

    // MARK: - Internal methods

    /// Disconnect all flow.
    public func disconnect() {
        disposeBag = DisposeBag()
        disconnectAll.onNext(())
    }

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
            }.map { characteristic in
                guard let data = characteristic.value else {
                    throw BluetoothError.characteristicReadFailed(
                        characteristic,
                        BleServiceDriverErrors.emptyResponse)
                }
                return data
            }
            .takeUntil(disconnectAll)
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
            }.take(1).asSingle().map { characteristic in
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
            }.take(1).asSingle().map { _ in
                return Data()
            }
    }

    // MARK: - Private methods

    /// Check current conenction state if connection or disconnect wait, when send event to peripheralEvent.
    /// If connected then send event from peripheralEvent.
    /// - returns: Peripheral as observable value.
    private func getConnectedPeripheral() -> Observable<Peripheral> {
        lock.lock()
        if let peripheralValue = try? peripheralEvent.value() {
            if peripheralValue.state != .connecting && !peripheralValue.isConnected {
                disposeBag = DisposeBag()
                peripheralValue.establishConnection()
                    .subscribe(onNext: peripheralEvent.onNext)
                    .disposed(by: disposeBag)
            }
        }
        lock.unlock()
        return peripheralEvent.asObservable().filter { $0.isConnected }
    }

    /// Method which connects to device (if needed) and discover requested characteristic.
    /// - parameter serviceUUID: UUID of requested service.
    /// - parameter characteristicUUID: UUID of requested characteristic.
    /// - returns: Characteristic as observable value.
    private func connectToDeviceAndDiscoverCharacteristic(
        serviceUUID: String,
        characteristicUUID: String
    ) -> Observable<Characteristic> {
        return getConnectedPeripheral().flatMap { peripheral -> Observable<Characteristic> in
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
