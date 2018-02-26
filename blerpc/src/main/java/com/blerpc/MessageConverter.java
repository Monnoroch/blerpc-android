package com.blerpc;

import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;

/** Represents a service that allows you to interact with the sensor via the Android BLE API. */
public interface MessageConverter {
    /**
     * Provides a way to convert a top level request to the byte representation
     * for sending to the remote device via {@link android.bluetooth.BluetoothGattCharacteristic}.
     *
     * @param methodDescriptor descriptor received from the {@link com.google.protobuf.Service}.
     * @param message request to sent.
     * @return the raw bytes that represent the submitted request for sending to the remote device.
     * @throws CouldNotConvertMessageException when serializing failed.
     */
    byte[] serializeRequest(MethodDescriptor methodDescriptor, Message message) throws CouldNotConvertMessageException;

    /**
     * Provides a way to convert a received bytes from the remote to device to the top level response.
     *
     * @param methodDescriptor descriptor received from the {@link com.google.protobuf.Service}.
     * @param responsePrototype method response message prototype received from the {@link com.google.protobuf.RpcChannel}.
     * @param value row value received from {@link android.bluetooth.BluetoothGattCharacteristic}.
     * @return the top-level response that represents the received data.
     * @throws CouldNotConvertMessageException when deserializing failed.
     */
    Message deserializeResponse(MethodDescriptor methodDescriptor, Message responsePrototype, byte[] value)
            throws CouldNotConvertMessageException;
}
