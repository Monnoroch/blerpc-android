package com.blerpc;

import static com.blerpc.Assert.assertError;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.content.Context;
import android.os.Handler;
import com.blerpc.device.test.proto.TestBleService;
import com.blerpc.device.test.proto.TestBleWriteRequest;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link ServiceStubFactory}.
 */
@RunWith(MockitoJUnitRunner.class)
public class ServiceStubFactoryTest {

    private static final String DEVICE_ADDRESS = "address";
    private static final String SECOND_DEVICE_ADDRESS = "second_address";

    private ServiceStubFactory serviceStubFactory;
    private Descriptors.MethodDescriptor methodWriteChar = TestBleService.getDescriptor().findMethodByName("TestWriteChar");
    @Mock RpcCallback<Message> callback;
    @Mock BluetoothDevice bluetoothDevice;
    @Mock BluetoothDevice bluetoothDeviceSecond;
    @Mock BluetoothGatt bluetoothGatt;
    @Mock Context context;
    @Mock MessageConverter messageConverter;
    @Mock Handler handler;
    @Mock Logger logger;

    /**
     * Set up.
     */
    @Before
    public void setUp() {
        initMocks(this);
        doAnswer(invocationOnMock -> {
            ((Runnable) invocationOnMock.getArgument(0)).run();
            return null;
        }).when(handler).post(any());
        when(bluetoothDevice.getAddress()).thenReturn(DEVICE_ADDRESS);
        when(bluetoothDevice.connectGatt(eq(context), anyBoolean(), any(BluetoothGattCallback.class))).thenReturn(bluetoothGatt);
        when(bluetoothDeviceSecond.connectGatt(eq(context), anyBoolean(), any(BluetoothGattCallback.class))).thenReturn(bluetoothGatt);
        serviceStubFactory = ServiceStubFactory.getInstance(context, messageConverter, handler, handler, logger);
    }

    /**
     * Tear down.
     */
    @After
    public void tearDown() {
        try {
            ServiceStubFactory.clearInstance();
        } catch (RuntimeException exception) {
            //Do nothing.
        }
    }

    @Test
    public void getInstance_secondTime() {
        assertError(() -> ServiceStubFactory.getInstance(context, messageConverter, handler, handler, logger), "Factory instance already exists");
    }

    @Test
    public void clearInstance() {
        ServiceStubFactory.clearInstance();
        ServiceStubFactory.getInstance(context, messageConverter, handler, handler, logger);
    }

    @Test
    public void clearInstance_instanceDoesntExists() {
        ServiceStubFactory.clearInstance();
        assertError(ServiceStubFactory::clearInstance, "Factory instance doesn't exist");
    }

    @Test
    public void testProvideService() throws Exception {
        assertThat(serviceStubFactory.provideService(bluetoothDevice, TestBleService.class)).isInstanceOf(TestBleService.class);
    }

    @Test
    public void testProvideService_equalDevices() throws Exception {
        TestBleService serviceFirst = (TestBleService) serviceStubFactory.provideService(bluetoothDevice, TestBleService.class);
        serviceFirst.callMethod(
                methodWriteChar,
                new BleRpcController(),
                TestBleWriteRequest.getDefaultInstance(),
                callback
        );
        // Set second device address equal first device address. ServiceStubFactory will take second device like first.
        when(bluetoothDeviceSecond.getAddress()).thenReturn(DEVICE_ADDRESS);
        TestBleService serviceSecond = (TestBleService) serviceStubFactory.provideService(bluetoothDeviceSecond, TestBleService.class);
        serviceSecond.callMethod(
                methodWriteChar,
                new BleRpcController(),
                TestBleWriteRequest.getDefaultInstance(),
                callback
        );

        verify(bluetoothDevice).connectGatt(eq(context), anyBoolean(), any(BluetoothGattCallback.class));
        // Check second device wasn't connected, because ServiceStubFactory think it's first device and connect to it.
        verify(bluetoothDeviceSecond, never()).connectGatt(eq(context), anyBoolean(), any(BluetoothGattCallback.class));
    }

    @Test
    public void testProvideService_differentDevices() throws Exception {
        TestBleService serviceFirst = (TestBleService) serviceStubFactory.provideService(bluetoothDevice, TestBleService.class);
        serviceFirst.callMethod(
                methodWriteChar,
                new BleRpcController(),
                TestBleWriteRequest.getDefaultInstance(),
                callback
        );

        when(bluetoothDeviceSecond.getAddress()).thenReturn(SECOND_DEVICE_ADDRESS);
        TestBleService serviceSecond = (TestBleService) serviceStubFactory.provideService(bluetoothDeviceSecond, TestBleService.class);
        serviceSecond.callMethod(
                methodWriteChar,
                new BleRpcController(),
                TestBleWriteRequest.getDefaultInstance(),
                callback
        );

        verify(bluetoothDevice).connectGatt(eq(context), anyBoolean(), any(BluetoothGattCallback.class));
        verify(bluetoothDeviceSecond).connectGatt(eq(context), anyBoolean(), any(BluetoothGattCallback.class));
    }

    @Test
    public void testProvideService_incorrectClass() throws Exception {
        assertError(() -> serviceStubFactory.provideService(bluetoothDevice, String.class), "Service class is incorrect");
    }

    @Test
    public void testCallDisconnectCloseBluetoothGatt() throws Exception {
        TestBleService testBleService = (TestBleService) serviceStubFactory.provideService(bluetoothDevice, TestBleService.class);
        testBleService.callMethod(
                methodWriteChar,
                new BleRpcController(),
                TestBleWriteRequest.getDefaultInstance(),
                callback
        );
        verify(bluetoothDevice).connectGatt(eq(context), anyBoolean(), any(BluetoothGattCallback.class));
        serviceStubFactory.disconnect(DEVICE_ADDRESS);
        verify(bluetoothGatt).close();
    }

    @Test
    public void testDisconnectBeforeAnyCalls() {
        assertError(() -> serviceStubFactory.disconnect(DEVICE_ADDRESS), "Chanel with bluetooth device address doesn't exist");
    }
}
