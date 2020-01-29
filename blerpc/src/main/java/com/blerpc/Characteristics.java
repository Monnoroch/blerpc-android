package com.blerpc;

import static com.google.common.base.Preconditions.checkArgument;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import com.blerpc.proto.MethodType;
import java.util.Arrays;
import java.util.UUID;

/* Utilities for working with characteristics objects. */
class Characteristics {
  /* Validate characteristic and descriptor to be usable for a given method. */
  public static void validate(BluetoothGatt bluetoothGatt, UUID serviceId, UUID characteristicId,
      UUID descriptorId, MethodType methodType) throws BleValidationException {
    BluetoothGattService service = bluetoothGatt.getService(serviceId);
    if (service == null) {
      throw new BleValidationException("Device does not have service %s.", serviceId);
    }

    BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicId);
    if (characteristic == null) {
      throw new BleValidationException("Service %s does not have characteristic %s.",
          serviceId, characteristicId);
    }

    if (methodType.equals(MethodType.SUBSCRIBE)) {
      BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorId);
      if (descriptor == null) {
        throw new BleValidationException(
            "Characteristic %s in service service %s does not have descriptor %s.",
            characteristicId, serviceId, descriptorId);
      }
    }

    validateCharacteristicProperties(serviceId, characteristicId, characteristic, methodType);
  }

  private static void validateCharacteristicProperties(UUID serviceId, UUID characteristicId,
      BluetoothGattCharacteristic characteristic, MethodType methodType) throws BleValidationException {
    switch (methodType) {
      case READ: {
        if (!isReadable(characteristic)) {
          throw new BleValidationException("Characteristic %s on service %s is not readable.",
              characteristicId, serviceId);
        }
        break;
      }
      case WRITE: {
        if (!isWritable(characteristic)) {
          throw new BleValidationException("Characteristic %s on service %s is not writable.",
              characteristicId, serviceId);
        }
        break;
      }
      case SUBSCRIBE: {
        if (!isNotifiable(characteristic)) {
          throw new BleValidationException("Characteristic %s on service %s is not notifiable.",
              characteristicId, serviceId);
        }
        break;
      }
      default:
        break;
    }
  }

  private static boolean isReadable(BluetoothGattCharacteristic characteristic) {
    return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0;
  }

  private static boolean isWritable(BluetoothGattCharacteristic characteristic) {
    return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
        || (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
  }

  private static boolean isNotifiable(BluetoothGattCharacteristic characteristic) {
    return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
  }

  /* Read value of a descriptor. */
  public static void readValue(BluetoothGatt gatt, UUID serviceId, UUID characteristicId) throws BleApiException {
    BluetoothGattCharacteristic characteristic = getCharacteristic(gatt, serviceId, characteristicId);
    if (!gatt.readCharacteristic(characteristic)) {
      throw new BleApiException("Failed to read characteristic %s in service %s.", characteristicId, serviceId);
    }
  }

  /* Write value to a characteristic. */
  public static void writeValue(BluetoothGatt gatt, UUID serviceId, UUID characteristicId, byte[] value)
      throws BleApiException {
    BluetoothGattCharacteristic characteristic = getCharacteristic(gatt, serviceId, characteristicId);
    // characteristic.setValue always returns true (as of the date this code was written).
    checkArgument(characteristic.setValue(value), "Failed to set value \"%s\" for characteristic %s in service %s.",
        Arrays.toString(value), characteristicId, serviceId);
    if (!gatt.writeCharacteristic(characteristic)) {
      throw new BleApiException("Failed to write characteristic %s in service %s.", characteristicId, serviceId);
    }
  }

  /* Write value to a descriptor. */
  public static void writeDescriptorValue(BluetoothGatt gatt, UUID serviceId, UUID characteristicId, UUID descriptorId,
      byte[] value) throws BleApiException {
    BluetoothGattDescriptor descriptor = getDescriptor(gatt, serviceId, characteristicId, descriptorId);
    // descriptor.setValue always returns true (as of the date this code was written).
    checkArgument(
        descriptor.setValue(value),
        "Failed to set value \"%s\" for descriptor %s in characteristic %s in service %s.",
        Arrays.toString(value), descriptorId, characteristicId, serviceId);
    if (!gatt.writeDescriptor(descriptor)) {
      throw new BleApiException("Failed to write the descriptor %s in characteristic %s in service %s.",
          descriptorId, characteristicId, serviceId);
    }
  }

  public static void setNotification(BluetoothGatt gatt, UUID serviceId, UUID characteristicId, boolean enabled)
      throws BleApiException {
    BluetoothGattCharacteristic characteristic = getCharacteristic(gatt, serviceId, characteristicId);
    if (!gatt.setCharacteristicNotification(characteristic, enabled)) {
      throw new BleApiException("Failed to enable notification for characteristic %s in service %s.",
          characteristicId, serviceId);
    }
  }

  /* Get descriptor object by it's ID. It must already be validated with {@link #validate}. */
  private static BluetoothGattDescriptor getDescriptor(BluetoothGatt gatt, UUID serviceId,
      UUID characteristicId, UUID descriptorId) {
    BluetoothGattCharacteristic characteristic = getCharacteristic(gatt, serviceId, characteristicId);
    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorId);
    checkArgument(descriptor != null, "Characteristic %s in service %s does not have descriptor %s.",
        characteristicId, serviceId, descriptorId);
    return descriptor;
  }

  /* Get characteristic object by it's ID. It must already be validated with {@link #validate}. */
  private static BluetoothGattCharacteristic getCharacteristic(BluetoothGatt gatt, UUID serviceId,
      UUID characteristicId) {
    BluetoothGattService service = getService(gatt, serviceId);
    BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicId);
    checkArgument(characteristic != null, "Service %s does not have characteristic %s.", serviceId, characteristicId);
    return characteristic;
  }

  /* Get service object by it's ID. It must already be validated with {@link #validate}. */
  private static BluetoothGattService getService(BluetoothGatt gatt, UUID serviceId) {
    BluetoothGattService service = gatt.getService(serviceId);
    checkArgument(service != null, "Device does not have service %s.", serviceId);
    return service;
  }

  /**
   * An exception to be thrown when a BleRpc method is invalid.
   */
  public static class BleValidationException extends Exception {
    public BleValidationException(String format, Object... args) {
      super(String.format(format, args));
    }
  }

  /**
   * An exception to be thrown when a BLE API call has failed.
   */
  public static class BleApiException extends Exception {
    public BleApiException(String format, Object... args) {
      super(String.format(format, args));
    }
  }
}
