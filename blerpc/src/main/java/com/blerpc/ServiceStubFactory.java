package com.blerpc;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import com.google.common.base.Preconditions;
import com.google.protobuf.RpcChannel;
import com.google.protobuf.Service;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Factory for creating com.google.protobuf. Service objects and connecting to device.
 * Only one class object can be created at the same time.
 * When object of this class becomes unnecessary, clearInstance() method remove class object to free memory.
 */
public class ServiceStubFactory {

    private static ServiceStubFactory serviceStubFactory = null;
    private static ConcurrentHashMap<String, BleRpcChannel> bleRpcChannels = new ConcurrentHashMap<>();

    private Context context;
    private MessageConverter messageConverter;
    private Handler workHandler;
    private Handler listenerHandler;
    private Logger logger;

    private ServiceStubFactory(Context context,
                               MessageConverter messageConverter,
                               Handler workHandler,
                               Handler listenerHandler,
                               Logger logger
    ) {
        this.context = context;
        this.messageConverter = messageConverter;
        this.workHandler = workHandler;
        this.listenerHandler = listenerHandler;
        this.logger = logger;
    }

    /**
     * Get a {@link ServiceStubFactory}.
     *
     * @param context          an application context.
     * @param messageConverter a {@link MessageConverter} for serializing requests and deserializing responses.
     * @param workHandler      a handler to run all channel's code.
     * @param listenerHandler  a handler run rpc callbacks.
     * @param logger           a logger for debug logging.
     * @return {@link ServiceStubFactory} object.
     */
    public static synchronized ServiceStubFactory getInstance(Context context,
                                                 MessageConverter messageConverter,
                                                 Handler workHandler,
                                                 Handler listenerHandler,
                                                 Logger logger
    ) {
        Preconditions.checkState(serviceStubFactory == null);
        serviceStubFactory = new ServiceStubFactory(
                context, messageConverter, workHandler, listenerHandler, logger
        );
        return serviceStubFactory;
    }

    /**
     * Disconnect from all devices and clear a {@link ServiceStubFactory} instance.
     */
    public static void clearInstance() {
        Preconditions.checkNotNull(serviceStubFactory);
        disconnectAll();
        serviceStubFactory = null;
    }

    /**
     * Get com.google.protobuf.Service object.
     *
     * @param bluetoothDevice - a {@link BluetoothDevice} to connect to.
     * @param serviceClass - class for creating new stub.
     * @return - com.google.protobuf.Service object.
     */
    public Service provideService(BluetoothDevice bluetoothDevice, Class<?> serviceClass) {
        String deviceAddress = bluetoothDevice.getAddress();
        if (!bleRpcChannels.containsKey(deviceAddress)) {
            bleRpcChannels.putIfAbsent(
                    deviceAddress,
                    new BleRpcChannel(bluetoothDevice, context, messageConverter, workHandler, listenerHandler, logger)
            );
        }
        try {
            Method newStub = serviceClass.getMethod("newStub", new Class[]{RpcChannel.class});
            return (Service) newStub.invoke(null, bleRpcChannels.get(deviceAddress));
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new RuntimeException("Service class is incorrect");
        }
    }

    /**
     * Close channel with bluetooth device connection.
     *
     * @param deviceAddress - bluetooth device mac address to disconnect from.
     */
    public void disconnect(String deviceAddress) {
        Preconditions.checkState(bleRpcChannels.containsKey(deviceAddress));
        bleRpcChannels.get(deviceAddress).reset();
        bleRpcChannels.remove(deviceAddress);
    }

    private static void disconnectAll() {
        for (BleRpcChannel channel : bleRpcChannels.values()) {
            channel.reset();
        }
        bleRpcChannels.clear();
    }
}
