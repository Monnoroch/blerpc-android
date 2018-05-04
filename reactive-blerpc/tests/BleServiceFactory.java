package com.blerpc.reactive;

/** Factory for creating aura ble services. */
public class BleServiceFactory {

  private final com.blerpc.ServiceStubFactory serviceStubFactory;
  private final java.util.logging.Logger logger;

  /**
   * Create {@link BleServiceFactory}.
   *
   * @param serviceStubFactory {@link BleServiceFactory} object.
   * @param logger - for logging errors.
   */
  public BleServiceFactory(com.blerpc.ServiceStubFactory serviceStubFactory, java.util.logging.Logger logger) {
    this.serviceStubFactory = serviceStubFactory;
    this.logger = logger;
  }

  /**
   * Create {@link com.device.proto.RxBatteryService}.
   *
   * @param bluetoothDevice - current bluetooth device for connection.
   * @return {@link com.device.proto.RxBatteryService} object.
   */
  public com.device.proto.RxBatteryService createBatteryService(android.bluetooth.BluetoothDevice bluetoothDevice) {
    return new com.device.proto.RxBatteryService(
        (com.device.proto.BatteryService)
            serviceStubFactory.provideService(
                bluetoothDevice, com.device.proto.BatteryService.class),
        logger);
  }
}
