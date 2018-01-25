package com.blerpc;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import com.blerpc.device.test.proto.TestBleReadRequest;
import com.blerpc.device.test.proto.TestBleReadResponse;
import com.blerpc.device.test.proto.TestBleService;
import com.blerpc.device.test.proto.TestBleSubscribeRequest;
import com.blerpc.device.test.proto.TestBleSubscribeResponse;
import com.blerpc.device.test.proto.TestBleWriteRequest;
import com.blerpc.device.test.proto.TestBleWriteResponse;
import com.blerpc.proto.BleCharacteristicRule;
import com.blerpc.proto.Blerpc;
import com.blerpc.proto.MethodType;
import com.google.protobuf.DescriptorProtos.MethodOptions;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link BleRpcChannel}.
 */
@RunWith(MockitoJUnitRunner.class)
public class BleRpcChannelTest {

    private static final byte[] TEST_READ_RESPONSE_BYTES = new byte[]{30, 35};
    private static final TestBleReadResponse TEST_READ_RESPONSE = TestBleReadResponse.newBuilder()
        .setValue("TEST_READ_RESPONSE")
        .build();
    private static final TestBleWriteRequest TEST_WRITE_REQUEST = TestBleWriteRequest.newBuilder()
        .setValue("TEST_WRITE_REQUEST")
        .build();
    private static final byte[] TEST_WRITE_REQUEST_BYTES = new byte[]{40, 45};
    private static final TestBleWriteRequest TEST_WRITE_REQUEST2 = TestBleWriteRequest.newBuilder()
        .setValue("TEST_WRITE_REQUEST2")
        .build();
    private static final byte[] TEST_WRITE_REQUEST_BYTES2 = new byte[]{60, 65};
    private static final byte[] TEST_WRITE_RESPONSE_BYTES = new byte[]{50, 55};
    private static final TestBleWriteResponse TEST_WRITE_RESPONSE = TestBleWriteResponse.newBuilder()
        .setValue("TEST_WRITE_RESPONSE")
        .build();
    private static final byte[] TEST_SUBSCRIBE_RESPONSE_BYTES = new byte[]{70, 75};
    private static final TestBleSubscribeResponse TEST_SUBSCRIBE_RESPONSE = TestBleSubscribeResponse.newBuilder()
        .setValue("TEST_SUBSCRIBE_RESPONSE")
        .build();
    private static final byte[] TEST_SUBSCRIBE_RESPONSE_BYTES2 = new byte[]{80, 85};
    private static final TestBleSubscribeResponse TEST_SUBSCRIBE_RESPONSE2 = TestBleSubscribeResponse.newBuilder()
        .setValue("TEST_SUBSCRIBE_RESPONSE2")
        .build();
    private static final int TEST_UNKNOWN_STATE =
        BluetoothProfile.STATE_CONNECTED + BluetoothProfile.STATE_DISCONNECTED + 1;
    private static final int TEST_STATUS_NOT_SUCCESS = BluetoothGatt.GATT_SUCCESS + 1;

    private static final UUID TEST_SERVICE = UUID.fromString(TestBleService.getDescriptor().getOptions()
        .getExtension(Blerpc.service).getUuid());
    private static final UUID TEST_CHARACTERISTIC = UUID.fromString(
        TestBleService.getDescriptor().findMethodByName("TestWriteChar").getOptions()
            .getExtension(Blerpc.characteristic).getUuid());
    private static final UUID TEST_CHARACTERISTIC2 = UUID.fromString(
        TestBleService.getDescriptor().findMethodByName("TestWriteChar2").getOptions()
            .getExtension(Blerpc.characteristic).getUuid());
    private static final UUID TEST_DESCRIPTOR = UUID.fromString(
        TestBleService.getDescriptor().findMethodByName("TestSubscribeChar").getOptions()
            .getExtension(Blerpc.characteristic).getDescriptorUuid());
    private static final UUID TEST_DESCRIPTOR2 = UUID.fromString(
        TestBleService.getDescriptor().findMethodByName("TestSubscribeChar2").getOptions()
            .getExtension(Blerpc.characteristic).getDescriptorUuid());

    private static final byte[] TEST_ENABLE_NOTIFICATION_VALUE = new byte[]{1};
    private static final byte[] TEST_DISABLE_NOTIFICATION_VALUE = new byte[]{2};

    @Mock private MessageConverter messageConverter;
    @Mock private BluetoothDevice bluetoothDevice;
    @Mock private Context context;
    @Mock private BluetoothGatt bluetoothGatt;
    @Mock private BluetoothGattService gattService;
    @Mock private BluetoothGattCharacteristic characteristic;
    @Mock private BluetoothGattCharacteristic characteristic2;
    @Mock private BluetoothGattDescriptor descriptor;
    @Mock private BluetoothGattDescriptor descriptor2;
    @Mock private RpcCallback<Message> callback;
    @Mock private RpcCallback<Message> callback2;
    @Mock private MethodDescriptor methodUnsupported;
    @Mock private Handler listenerHandler;

    private MethodDescriptor methodReadChar = TestBleService.getDescriptor().findMethodByName("TestReadChar");
    private MethodDescriptor methodWriteChar = TestBleService.getDescriptor().findMethodByName("TestWriteChar");
    private MethodDescriptor methodSubscribeChar = TestBleService.getDescriptor().findMethodByName("TestSubscribeChar");
    private MethodDescriptor methodSubscribeCharCopy =
        TestBleService.getDescriptor().findMethodByName("TestSubscribeCharCopy");
    private MethodDescriptor methodWriteChar2 = TestBleService.getDescriptor().findMethodByName("TestWriteChar2");
    private MethodDescriptor methodSubscribeChar2 =
        TestBleService.getDescriptor().findMethodByName("TestSubscribeChar2");
    private BleRpcController controller = new BleRpcController();
    private BleRpcController controller2 = new BleRpcController();
    private ArgumentCaptor<BluetoothGattCallback> bluetoothCallback =
        ArgumentCaptor.forClass(BluetoothGattCallback.class);

    private BleRpcChannel channel;

    /**
     * Set up unsupported method.
     */
    @Before
    public void setUpUnsupportedMethod() throws Exception {
        when(methodUnsupported.getOptions()).thenReturn(MethodOptions.newBuilder()
            .setExtension(Blerpc.characteristic, BleCharacteristicRule.newBuilder()
                .setType(MethodType.UNKNOWN)
                .setUuid("7c722b28-9b20-4b53-9b11-287510dcd2f8")
                .build())
            .build());
    }

    /**
     * Set up handlers and the channel.
     */
    @Before
    public void setUp() throws Exception {
        Handler workHandler = Mockito.mock(Handler.class);

        doAnswer(invocationOnMock -> {
            ((Runnable) invocationOnMock.getArgument(0)).run();
            return null;
        }).when(workHandler).post(any());
        doAnswer(invocationOnMock -> {
            ((Runnable) invocationOnMock.getArgument(0)).run();
            return null;
        }).when(listenerHandler).post(any());

        channel = new BleRpcChannel(bluetoothDevice, context, messageConverter, workHandler, listenerHandler,
            Mockito.mock(Logger.class));
    }

    /**
     * Set up constants.
     */
    @BeforeClass
    public static void setUpConstants() {
        BleRpcChannel.ENABLE_NOTIFICATION_VALUE = TEST_ENABLE_NOTIFICATION_VALUE;
        BleRpcChannel.DISABLE_NOTIFICATION_VALUE = TEST_DISABLE_NOTIFICATION_VALUE;
    }

    /**
     * Set up bluetooth API mock.
     */
    @Before
    public void setUpGatt() throws Exception {
        when(bluetoothDevice.connectGatt(eq(context), anyBoolean(), bluetoothCallback.capture()))
            .thenReturn(bluetoothGatt);
        when(bluetoothGatt.discoverServices()).thenReturn(true);
        when(bluetoothGatt.getService(TEST_SERVICE)).thenReturn(gattService);
        when(bluetoothGatt.readCharacteristic(characteristic)).thenReturn(true);
        when(bluetoothGatt.writeCharacteristic(characteristic)).thenReturn(true);
        when(bluetoothGatt.writeCharacteristic(characteristic2)).thenReturn(true);
        when(bluetoothGatt.setCharacteristicNotification(characteristic, true)).thenReturn(true);
        when(bluetoothGatt.setCharacteristicNotification(characteristic2, true)).thenReturn(true);
        when(bluetoothGatt.writeDescriptor(descriptor)).thenReturn(true);
        when(bluetoothGatt.writeDescriptor(descriptor2)).thenReturn(true);
        when(gattService.getCharacteristic(TEST_CHARACTERISTIC)).thenReturn(characteristic);
        when(gattService.getCharacteristic(TEST_CHARACTERISTIC2)).thenReturn(characteristic2);
        when(characteristic.getUuid()).thenReturn(TEST_CHARACTERISTIC);
        when(characteristic.setValue(any(byte[].class))).thenReturn(true);
        when(characteristic.getDescriptor(TEST_DESCRIPTOR)).thenReturn(descriptor);
        when(characteristic.getProperties()).thenReturn(
            BluetoothGattCharacteristic.PROPERTY_READ
            | BluetoothGattCharacteristic.PROPERTY_WRITE
            | BluetoothGattCharacteristic.PROPERTY_NOTIFY);
        when(characteristic2.getUuid()).thenReturn(TEST_CHARACTERISTIC2);
        when(characteristic2.setValue(any(byte[].class))).thenReturn(true);
        when(characteristic2.getDescriptor(TEST_DESCRIPTOR2)).thenReturn(descriptor2);
        when(characteristic2.getProperties()).thenReturn(
            BluetoothGattCharacteristic.PROPERTY_READ
            | BluetoothGattCharacteristic.PROPERTY_WRITE
            | BluetoothGattCharacteristic.PROPERTY_NOTIFY);
        when(descriptor.getCharacteristic()).thenReturn(characteristic);
        when(descriptor.getUuid()).thenReturn(TEST_DESCRIPTOR);
        when(descriptor.setValue(any(byte[].class))).thenReturn(true);
        when(descriptor2.getCharacteristic()).thenReturn(characteristic2);
        when(descriptor2.getUuid()).thenReturn(TEST_DESCRIPTOR2);
        when(descriptor2.setValue(any(byte[].class))).thenReturn(true);
    }

    /**
     * Set up default message conversions.
     */
    @Before
    public void setUpMessageConverter() throws Exception {
        when(messageConverter.serializeRequest(any(), any())).thenReturn(new byte[]{});
        when(messageConverter.deserializeResponse(any(), any(byte[].class)))
            .thenReturn(TestBleReadResponse.getDefaultInstance());
    }

    @Test
    public void testUnsupportedMethod() throws Exception {
        callWriteMethod(methodUnsupported, controller);
        assertCallFailed(controller);
        verifyNoWrite();
    }

    @Test
    public void testCallCallbackWithDefaultInstanceWhenFailed() throws Exception {
        callFailedMethod(controller, callback);
        assertCallFailed(controller);
        verifyCalledWithDefault(callback);
        verifyNoReadWrite();
    }

    @Test
    public void testConnectOnFirstCall() throws Exception {
        callMethod(callback);
        verify(bluetoothDevice).connectGatt(eq(context), anyBoolean(), any());
        verifyNoReadWrite();
        verifyNoCalls(callback);
    }

    @Test
    public void testOnlyConnectOnFirstCall() throws Exception {
        callMethod();
        callMethod();
        verify(bluetoothDevice, times(1)).connectGatt(eq(context), anyBoolean(), any());
    }

    @Test
    public void testDoNotCallUntilConnected() throws Exception {
        callMethod(callback);
        verifyNoReadWrite();
        verifyNoCalls(callback);
    }

    @Test
    public void testResetBeforeConnected() throws Exception {
        channel.reset();
        verify(bluetoothGatt, never()).close();
    }

    @Test
    public void testIgnoreUnknownState() throws Exception {
        callMethod();
        onConnectionStateChange(0, TEST_UNKNOWN_STATE);
        verify(bluetoothGatt, never()).discoverServices();
        verifyNoReadWrite();
    }

    @Test
    public void testDoNotConnectAndFailWhenDisconnectedState() throws Exception {
        callMethod(controller);
        onConnectionStateChange(0, BluetoothProfile.STATE_DISCONNECTED);
        assertFailBeforeDiscoveringServices(controller);
        verifyReset();
    }

    @Test
    public void testDoNotConnectAndFailWhenStatusNotSuccess() throws Exception {
        callMethod(controller);
        onConnectionStateChange(TEST_STATUS_NOT_SUCCESS, BluetoothProfile.STATE_CONNECTED);
        assertFailBeforeDiscoveringServices(controller);
        verifyReset();
    }

    @Test
    public void testDoNotConnectAndFailWhenDiscoveryFailed() throws Exception {
        callMethod(controller);
        when(bluetoothGatt.discoverServices()).thenReturn(false);
        onConnectionStateChange(BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);
        verifyNoReadWrite();
        assertCallFailed(controller);
        verifyReset();
    }

    @Test
    public void testDoNotConnectAndFailWhenDiscoveryStatusNotSuccess() throws Exception {
        callMethod(controller);
        onConnectionStateChange(BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);
        onServicesDiscovered(TEST_STATUS_NOT_SUCCESS);
        verifyNoReadWrite();
        assertCallFailed(controller);
        verifyReset();
    }

    @Test
    public void testSkipCanceledCall() throws Exception {
        callMethod(controller, callback);
        controller.startCancel();
        finishConnecting();
        verifyNoReadWrite();
        assertCallSucceeded(controller);
        verifyCalledWithDefault(callback);
    }

    @Test
    public void testFailIfServiceNotFound() throws Exception {
        when(bluetoothGatt.getService(TEST_SERVICE)).thenReturn(null);
        callMethod(controller);
        finishConnecting();
        verifyNoReadWrite();
        assertCallFailed(controller);
    }

    @Test
    public void testFailIfCharacteristicNotFound() throws Exception {
        when(gattService.getCharacteristic(TEST_CHARACTERISTIC)).thenReturn(null);
        callMethod(controller);
        finishConnecting();
        verifyNoReadWrite();
        assertCallFailed(controller);
    }

    @Test
    public void testReadFailIfCharacteristicNotReadable() throws Exception {
        when(characteristic.getProperties()).thenReturn(0);
        callReadMethod(controller);
        finishConnecting();
        verifyNoRead();
        assertCallFailed(controller);
    }

    @Test
    public void testReadCalled() throws Exception {
        callReadMethod(controller);
        finishConnecting();
        verify(bluetoothGatt).readCharacteristic(characteristic);
    }

    @Test
    public void testReadFailed() throws Exception {
        when(bluetoothGatt.readCharacteristic(characteristic)).thenReturn(false);
        callReadMethod(controller);
        finishConnecting();
        assertCallFailed(controller);
    }

    @Test
    public void testReadStatusNotSuccess() throws Exception {
        callReadMethod(controller);
        finishConnecting();
        onCharacteristicReadFail();
        assertCallFailed(controller);
    }

    @Test
    public void testReadDeserializeFailed() throws Exception {
        when(characteristic.getValue()).thenReturn(TEST_READ_RESPONSE_BYTES);
        when(messageConverter.deserializeResponse(methodReadChar, TEST_READ_RESPONSE_BYTES))
            .thenThrow(CouldNotConvertMessageException.deserializeResponse("Error"));
        callReadMethod(methodReadChar, controller, callback);
        finishConnecting();
        onCharacteristicRead();
        assertCallFailed(controller);
    }

    @Test
    public void testReadSuccess() throws Exception {
        when(characteristic.getValue()).thenReturn(TEST_READ_RESPONSE_BYTES);
        when(messageConverter.deserializeResponse(methodReadChar, TEST_READ_RESPONSE_BYTES))
            .thenReturn(TEST_READ_RESPONSE);
        callReadMethod(methodReadChar, controller, callback);
        finishConnecting();
        onCharacteristicRead();
        assertCallSucceeded(controller);
        verify(callback).run(TEST_READ_RESPONSE);
    }

    @Test
    public void testWriteFailIfCharacteristicNotWritable() throws Exception {
        when(characteristic.getProperties()).thenReturn(0);
        callWriteMethod(methodWriteChar, controller, TEST_WRITE_REQUEST);
        finishConnecting();
        verifyNoWrite();
        assertCallFailed(controller);
    }

    @Test
    public void testWriteSerializeFailed() throws Exception {
        when(messageConverter.serializeRequest(methodWriteChar, TEST_WRITE_REQUEST))
            .thenThrow(CouldNotConvertMessageException.serializeRequest("Error"));
        callWriteMethod(methodWriteChar, controller, TEST_WRITE_REQUEST);
        finishConnecting();
        verifyNoWrite();
        assertCallFailed(controller);
    }

    @Test
    public void testWriteCalled() throws Exception {
        when(messageConverter.serializeRequest(methodWriteChar, TEST_WRITE_REQUEST))
            .thenReturn(TEST_WRITE_REQUEST_BYTES);
        callWriteMethod(methodWriteChar, TEST_WRITE_REQUEST);
        finishConnecting();
        verify(characteristic).setValue(TEST_WRITE_REQUEST_BYTES);
        verify(bluetoothGatt).writeCharacteristic(characteristic);
    }

    @Test
    public void testWriteFailed() throws Exception {
        when(bluetoothGatt.writeCharacteristic(characteristic)).thenReturn(false);
        callWriteMethod(methodWriteChar, controller);
        finishConnecting();
        assertCallFailed(controller);
    }

    @Test
    public void testWriteStatusNotSuccess() throws Exception {
        callWriteMethod(methodWriteChar, controller);
        finishConnecting();
        onCharacteristicWriteFail(characteristic);
        assertCallFailed(controller);
    }

    @Test
    public void testWriteDeserializeFailed() throws Exception {
        when(characteristic.getValue()).thenReturn(TEST_WRITE_RESPONSE_BYTES);
        when(messageConverter.deserializeResponse(methodWriteChar, TEST_WRITE_RESPONSE_BYTES))
            .thenThrow(CouldNotConvertMessageException.deserializeResponse("Error"));
        callWriteMethod(methodWriteChar, controller);
        finishConnecting();
        onCharacteristicWrite(characteristic);
        assertCallFailed(controller);
    }

    @Test
    public void testWriteSuccess() throws Exception {
        when(characteristic.getValue()).thenReturn(TEST_WRITE_RESPONSE_BYTES);
        when(messageConverter.deserializeResponse(methodWriteChar, TEST_WRITE_RESPONSE_BYTES))
            .thenReturn(TEST_WRITE_RESPONSE);
        callWriteMethod(methodWriteChar, controller, callback);
        finishConnecting();
        onCharacteristicWrite(characteristic);
        assertCallSucceeded(controller);
        verify(callback).run(TEST_WRITE_RESPONSE);
    }

    @Test
    public void testCallsAreSequential() throws Exception {
        callWriteMethod(methodWriteChar);
        callWriteMethod(methodWriteChar2);
        finishConnecting();

        verify(bluetoothGatt).writeCharacteristic(characteristic);
        verifyNoWrite(characteristic2);
        onCharacteristicWrite(characteristic);
        verify(bluetoothGatt).writeCharacteristic(characteristic2);
    }

    @Test
    public void testLoopUntilNotCanceledCall() throws Exception {
        callWriteMethod(methodWriteChar, controller, callback);
        callWriteMethod(methodWriteChar2, controller2, callback2);
        controller.startCancel();
        finishConnecting();

        assertCallSucceeded(controller);
        verifyCalledWithDefault(callback);
        verify(bluetoothGatt).writeCharacteristic(characteristic2);
    }

    @Test
    public void testSubscribeIgnoreCanceledCall() throws Exception {
        callSubscribeMethod(controller, callback);
        controller.startCancel();
        finishConnecting();
        verifyNoSubscribe();
        assertCallSucceeded(controller);
        verifyNoCalls(callback);
    }

    @Test
    public void testSubscribeFailIfServiceNotFound() throws Exception {
        when(bluetoothGatt.getService(TEST_SERVICE)).thenReturn(null);
        callSubscribeMethod(controller);
        finishConnecting();
        verifyNoSubscribe();
        assertCallFailed(controller);
    }

    @Test
    public void testSubscribeFailIfCharacteristicNotFound() throws Exception {
        when(gattService.getCharacteristic(TEST_CHARACTERISTIC)).thenReturn(null);
        callSubscribeMethod(controller);
        finishConnecting();
        verifyNoSubscribe();
        assertCallFailed(controller);
    }

    @Test
    public void testSubscribeFailIfCharacteristicNotNotifiable() throws Exception {
        when(characteristic.getProperties()).thenReturn(0);
        callSubscribeMethod(controller);
        finishConnecting();
        verifyNoSubscribe();
        assertCallFailed(controller);
    }

    @Test
    public void testSubscribeFailIfDescriptorNotFound() throws Exception {
        when(characteristic.getDescriptor(TEST_DESCRIPTOR)).thenReturn(null);
        callSubscribeMethod(controller);
        finishConnecting();
        verifyNoSubscribe();
        assertCallFailed(controller);
    }

    @Test
    public void testSubscribeFailIfCouldNotEnableNotifications() throws Exception {
        when(bluetoothGatt.setCharacteristicNotification(characteristic, true)).thenReturn(false);
        callSubscribeMethod(controller);
        finishConnecting();
        verifyNoSubscribe();
        assertCallFailed(controller);
    }

    @Test
    public void testSubscribeCalled() throws Exception {
        callSubscribeMethod(controller);
        finishConnecting();
        verifySubscribe(descriptor);
    }

    @Test
    public void testSubscribeFailed() throws Exception {
        when(bluetoothGatt.writeDescriptor(descriptor)).thenReturn(false);
        callSubscribeMethod(controller);
        finishConnecting();
        assertCallFailed(controller);
    }

    @Test
    public void testSubscribeStatusNotSuccess() throws Exception {
        callSubscribeMethod(controller);
        finishConnecting();
        onSubscribeFail(descriptor);
        assertCallFailed(controller);
    }

    @Test
    public void testSubscribeSuccess() throws Exception {
        BleRpcController bleRpcController = spy(controller);
        callSubscribeMethod(bleRpcController, callback);
        verify(bleRpcController, never()).onSubscribeSuccess();
        finishSubscribing(descriptor);
        verify(bleRpcController).onSubscribeSuccess();
        verifyNoCalls(callback);
    }

    @Test
    public void testSubscribeCalled_onSubscribeSuccessCalled_notBleRpcController() throws Exception {
        RpcController bleRpcController = spy(controller);
        callSubscribeMethod(bleRpcController, callback);
        // If test fail it will throw ClassCastException: RpcController cannot be cast to BleRpcController.
        finishSubscribing(descriptor);
    }

    @Test
    public void testSubscribeCalled_afterActiveSubscriptionAlreadyExists_onSubscribeSuccessCalled() throws Exception {
        callSubscribeMethod(controller, callback);
        finishSubscribing(descriptor);

        BleRpcController bleRpcController = spy(controller2);
        callSubscribeMethod(bleRpcController, callback);
        verify(bleRpcController).onSubscribeSuccess();
    }

    @Test
    public void testSubscribeCalled_subscriptionAlreadyExists_onSubscribeSuccessCalled_notBleRpcCallback() throws Exception {
        callSubscribeMethod(controller, callback);
        finishSubscribing(descriptor);

        RpcController secondBleRpcController = spy(controller2);
        // If test fail it will throw ClassCastException: RpcController cannot be cast to BleRpcController.
        callSubscribeMethod(secondBleRpcController, callback);
    }

    @Test
    public void testSubscribeUnsubscribeWhenNoSubscribers() throws Exception {
        callSubscribeMethod(controller, callback);
        finishSubscribing(descriptor);
        controller.startCancel();
        onCharacteristicChanged(characteristic);
        verifyUnsubscribe(descriptor);
        verifyNoCalls(callback);
    }

    @Test
    public void testSubscribeUnsubscribeWhenDeserializeFailed() throws Exception {
        callSubscribeMethod(methodSubscribeChar, controller);
        finishSubscribing(descriptor);
        setUpSubscriptionDesetializeFailure(characteristic);
        onCharacteristicChanged(characteristic);
        verifyUnsubscribe(descriptor);
        assertCallFailed(controller);
    }

    @Test
    public void testSubscribeNotification() throws Exception {
        callSubscribeMethod(methodSubscribeChar, controller, callback);
        finishSubscribing(descriptor);
        when(characteristic.getValue()).thenReturn(TEST_SUBSCRIBE_RESPONSE_BYTES);
        when(messageConverter.deserializeResponse(methodSubscribeChar, TEST_SUBSCRIBE_RESPONSE_BYTES))
            .thenReturn(TEST_SUBSCRIBE_RESPONSE);
        onCharacteristicChanged(characteristic);
        assertCallSucceeded(controller);
        verify(callback).run(TEST_SUBSCRIBE_RESPONSE);
    }

    @Test
    public void testSubscribeMultipleNotifications() throws Exception {
        callSubscribeMethod(methodSubscribeChar, controller, callback);
        finishSubscribing(descriptor);
        when(characteristic.getValue()).thenReturn(TEST_SUBSCRIBE_RESPONSE_BYTES);
        when(messageConverter.deserializeResponse(methodSubscribeChar, TEST_SUBSCRIBE_RESPONSE_BYTES))
            .thenReturn(TEST_SUBSCRIBE_RESPONSE);
        onCharacteristicChanged(characteristic);
        assertCallSucceeded(controller);
        verify(callback).run(TEST_SUBSCRIBE_RESPONSE);
        when(characteristic.getValue()).thenReturn(TEST_SUBSCRIBE_RESPONSE_BYTES2);
        when(messageConverter.deserializeResponse(methodSubscribeChar, TEST_SUBSCRIBE_RESPONSE_BYTES2))
            .thenReturn(TEST_SUBSCRIBE_RESPONSE2);
        onCharacteristicChanged(characteristic);
        assertCallSucceeded(controller);
        verify(callback).run(TEST_SUBSCRIBE_RESPONSE2);
    }

    @Test
    public void testSubscribeUnsubscribeFailed() throws Exception {
        callSubscribeMethod(controller, callback);
        finishSubscribing(descriptor);
        when(bluetoothGatt.writeDescriptor(descriptor)).thenReturn(false);
        // Will cause onCharacteristicChanged to unsubscribe.
        setUpSubscriptionDesetializeFailure(characteristic);
        onCharacteristicChanged(characteristic);
        verifyUnsubscribe(descriptor);
        assertCallFailed(controller);
        verifyReset();
    }

    @Test
    public void testSubscribeUnsubscribeStatusNotSuccess() throws Exception {
        callSubscribeMethod(controller, callback);
        finishSubscribing(descriptor);
        // Will cause onCharacteristicChanged to unsubscribe.
        setUpSubscriptionDesetializeFailure(characteristic);
        onCharacteristicChanged(characteristic);
        onUnsubscribeFail(descriptor);
        assertCallFailed(controller);
        verifyReset();
    }

    @Test
    public void testFailSubscriptionWhenDisconnectedState() throws Exception {
        callSubscribeMethod(controller, callback);
        onConnectionStateChange(0, BluetoothProfile.STATE_DISCONNECTED);
        verify(callback, times(1)).run(any(Message.class));
        assertCallFailed(controller);
    }

    // Same as testSubscribeIgnoreCanceledCall, but after unsubscribing.
    @Test
    public void testSubscribeUnsubscribeWithNoSubscribers() throws Exception {
        callSubscribeMethod(controller, callback);
        finishSubscribing(descriptor);
        controller.startCancel();
        // Causes unsubscribe because all subscribers are cancelled.
        onCharacteristicChanged(characteristic);
        onUnsubscribe(descriptor);
        verifyUnsubscribed();
    }

    // Same as testSubscribeCalled, but after unsubscribing.
    @Test
    public void testSubscribeUnsubscribeWithSubscribers() throws Exception {
        callSubscribeMethod(controller, callback);
        finishSubscribing(descriptor);
        // Will cause onCharacteristicChanged to unsubscribe.
        setUpSubscriptionDesetializeFailure(characteristic);
        onCharacteristicChanged(characteristic);
        onUnsubscribe(descriptor);
        verifySubscribeSecondTime(descriptor);
    }

    @Test
    public void testSubscribeToCharacteristicOnlyOnce() throws Exception {
        callSubscribeMethod(methodSubscribeChar);
        callSubscribeMethod(methodSubscribeCharCopy);
        finishConnecting();
        verifySubscribe(descriptor);
    }

    @Test
    public void testSubscribeDifferentCharacteristicsWorkInParallel() throws Exception {
        callSubscribeMethod(methodSubscribeChar, controller, callback);
        callSubscribeMethod(methodSubscribeChar2, controller2, callback2);
        finishConnecting();
        onSubscribe(descriptor);
        onSubscribe(descriptor2);
        when(characteristic.getValue()).thenReturn(TEST_SUBSCRIBE_RESPONSE_BYTES);
        when(messageConverter.deserializeResponse(methodSubscribeChar, TEST_SUBSCRIBE_RESPONSE_BYTES))
            .thenReturn(TEST_SUBSCRIBE_RESPONSE);
        onCharacteristicChanged(characteristic);
        when(characteristic2.getValue()).thenReturn(TEST_SUBSCRIBE_RESPONSE_BYTES2);
        when(messageConverter.deserializeResponse(methodSubscribeChar2, TEST_SUBSCRIBE_RESPONSE_BYTES2))
            .thenReturn(TEST_SUBSCRIBE_RESPONSE2);
        onCharacteristicChanged(characteristic2);
        assertCallSucceeded(controller);
        verify(callback).run(TEST_SUBSCRIBE_RESPONSE);
        assertCallSucceeded(controller2);
        verify(callback2).run(TEST_SUBSCRIBE_RESPONSE2);
    }

    @Test
    public void testDontCallCallbackIfCancelled() throws Exception {
        reset(listenerHandler);
        when(listenerHandler.post(any())).thenReturn(true);
        callSubscribeMethod(methodSubscribeChar, controller, callback);
        finishSubscribing(descriptor);
        when(messageConverter.deserializeResponse(eq(methodSubscribeChar), any())).thenReturn(null);
        onCharacteristicChanged(characteristic);
        ArgumentCaptor<Runnable> callCallback = ArgumentCaptor.forClass(Runnable.class);
        verify(listenerHandler).post(callCallback.capture());
        controller.startCancel();
        callCallback.getValue().run();
        verifyNoCalls(callback);
    }

    @Test
    public void testValueChangedBeforeOnDescriptorWriteSubscribe() {
        callSubscribeMethod(controller, callback);
        finishConnecting();
        onCharacteristicChanged(characteristic);
        // If fail - java.lang.IllegalArgumentException: The characteristic f0cdaa72-0451-4000-b000-000000000000 is not subscribed.
    }

    @Test
    public void testValueChangedBeforeOnDescriptorWriteUnsubscribe() throws Exception {
        callSubscribeMethod(methodSubscribeChar, controller, callback);
        finishSubscribing(descriptor);
        // Will cause onCharacteristicChanged to unsubscribe.
        setUpSubscriptionDesetializeFailure(characteristic);
        onCharacteristicChanged(characteristic);
        // Call while unsubscribing.
        onCharacteristicChanged(characteristic);
        // If fail - java.lang.IllegalArgumentException: The characteristic f0cdaa72-0451-4000-b000-000000000000 is not subscribed.
    }

    @Test
    public void testValueChangedAfterReset() throws Exception {
        callSubscribeMethod(controller, callback);
        finishSubscribing(descriptor);
        channel.reset();
        onCharacteristicChanged(characteristic);
        // If fail - java.lang.IllegalArgumentException: There is no subscription calls group for characteristic
        // f0cdaa72-0451-4000-b000-000000000000.
        verifyNoCalls(callback);
    }

    @Test
    public void testDontCallCallbackWhenFailedIfCancelled() throws Exception {
        reset(listenerHandler);
        when(listenerHandler.post(any())).thenReturn(true);
        when(bluetoothGatt.getService(TEST_SERVICE)).thenReturn(null);
        callSubscribeMethod(methodSubscribeChar, controller, callback);
        finishConnecting();
        ArgumentCaptor<Runnable> callCallback = ArgumentCaptor.forClass(Runnable.class);
        verify(listenerHandler).post(callCallback.capture());
        controller.startCancel();
        callCallback.getValue().run();
        assertCallFailed(controller);
        verifyNoCalls(callback);
    }

    void setUpSubscriptionDesetializeFailure(BluetoothGattCharacteristic characteristic) throws Exception {
        when(characteristic.getValue()).thenReturn(TEST_SUBSCRIBE_RESPONSE_BYTES);
        when(messageConverter.deserializeResponse(methodSubscribeChar, TEST_SUBSCRIBE_RESPONSE_BYTES))
            .thenThrow(CouldNotConvertMessageException.deserializeResponse("Error"));
    }

    void verifySubscribe(BluetoothGattDescriptor descriptor) {
        verify(descriptor).setValue(TEST_ENABLE_NOTIFICATION_VALUE);
        verify(bluetoothGatt).writeDescriptor(descriptor);
    }

    void verifySubscribeSecondTime(BluetoothGattDescriptor descriptor) {
        verify(descriptor).setValue(TEST_ENABLE_NOTIFICATION_VALUE);
        verify(bluetoothGatt, times(2)).writeDescriptor(descriptor);
    }

    void verifyUnsubscribe(BluetoothGattDescriptor descriptor) {
        verify(descriptor).setValue(TEST_DISABLE_NOTIFICATION_VALUE);
        verify(bluetoothGatt, atLeast(2)).writeDescriptor(descriptor);
    }

    void assertFailBeforeDiscoveringServices(BleRpcController controller) {
        verify(bluetoothGatt, never()).discoverServices();
        verifyNoReadWrite();
        assertCallFailed(controller);
    }

    void callMethod() {
        callWriteMethod();
    }

    void callMethod(RpcCallback<Message> callback) {
        callWriteMethod(callback);
    }

    void callMethod(BleRpcController controller) {
        callWriteMethod(controller);
    }

    void callMethod(BleRpcController controller, RpcCallback<Message> callback) {
        callWriteMethod(controller, callback);
    }

    void callReadMethod(BleRpcController controller) {
        callReadMethod(methodReadChar, controller, callback);
    }

    void callReadMethod(MethodDescriptor method) {
        callReadMethod(method, controller, callback);
    }

    void callReadMethod(MethodDescriptor method, BleRpcController controller, RpcCallback<Message> callback) {
        channel.callMethod(method, controller, TestBleReadRequest.getDefaultInstance(),
            TestBleReadResponse.getDefaultInstance(), callback);
    }

    void callFailedMethod(BleRpcController controller, RpcCallback<Message> callback) {
        callWriteMethod(methodUnsupported, controller, callback);
    }

    void callWriteMethod() {
        callWriteMethod(controller);
    }

    void callWriteMethod(BleRpcController controller) {
        callWriteMethod(controller, callback);
    }

    void callWriteMethod(RpcCallback<Message> callback) {
        callWriteMethod(controller, callback);
    }

    void callWriteMethod(BleRpcController controller, RpcCallback<Message> callback) {
        callWriteMethod(methodWriteChar, controller, callback);
    }

    void callWriteMethod(MethodDescriptor method) {
        callWriteMethod(method, controller);
    }

    void callWriteMethod(MethodDescriptor method, BleRpcController controller) {
        callWriteMethod(method, controller, callback);
    }

    void callWriteMethod(MethodDescriptor method, TestBleWriteRequest request) {
        callWriteMethod(method, controller, request);
    }

    void callWriteMethod(MethodDescriptor method, BleRpcController controller, TestBleWriteRequest request) {
        callWriteMethod(method, controller, callback, request);
    }

    void callWriteMethod(MethodDescriptor method, BleRpcController controller, RpcCallback<Message> callback) {
        callWriteMethod(method, controller, callback, TestBleWriteRequest.getDefaultInstance());
    }

    void callWriteMethod(MethodDescriptor method, BleRpcController controller, RpcCallback<Message> callback,
        TestBleWriteRequest request) {
        channel.callMethod(method, controller, request, TestBleWriteResponse.getDefaultInstance(), callback);
    }

    void callSubscribeMethod(RpcController controller) {
        callSubscribeMethod(controller, callback);
    }

    void callSubscribeMethod(MethodDescriptor method) {
        callSubscribeMethod(method, controller, callback);
    }

    void callSubscribeMethod(RpcController controller, RpcCallback<Message> callback) {
        callSubscribeMethod(methodSubscribeChar, controller, callback);
    }

    void callSubscribeMethod(MethodDescriptor method, RpcController controller) {
        callSubscribeMethod(method, controller, callback);
    }

    void callSubscribeMethod(MethodDescriptor method, RpcController controller, RpcCallback<Message> callback) {
        channel.callMethod(method, controller, TestBleSubscribeRequest.getDefaultInstance(),
            TestBleSubscribeResponse.getDefaultInstance(), callback);
    }

    void verifyNoRead() {
        verify(bluetoothGatt, never()).readCharacteristic(any());
    }

    void verifyNoWrite() {
        verify(bluetoothGatt, never()).writeCharacteristic(any());
    }

    void verifyNoWrite(BluetoothGattCharacteristic characteristic) {
        verify(bluetoothGatt, never()).writeCharacteristic(characteristic);
    }

    void verifyNoReadWrite() {
        verifyNoRead();
        verifyNoWrite();
    }

    void verifyNoSubscribe() {
        verify(bluetoothGatt, never()).writeDescriptor(any());
    }

    void verifyNoCalls(RpcCallback<Message> callback) {
        verify(callback, never()).run(any());
    }

    void verifyCalledWithDefault(RpcCallback<Message> callback) {
        verify(callback).run(TestBleWriteResponse.getDefaultInstance());
    }

    void assertCallSucceeded(BleRpcController controller) {
        assertThat(controller.failed()).isFalse();
        assertThat(controller.errorText()).isNull();
    }

    void assertCallFailed(BleRpcController controller) {
        assertThat(controller.failed()).isTrue();
        assertThat(controller.errorText()).isNotNull();
    }

    void verifyReset() throws Exception {
        verify(bluetoothGatt).close();
        reset(bluetoothDevice);
        // {@link reset} not only resets interactions, but also stubbing, which we need to resetup.
        setUpGatt();
        callMethod(new BleRpcController());
        // If the channel was reset, it will reconnect on next method call.
        verify(bluetoothDevice).connectGatt(eq(context), anyBoolean(), any());
    }

    void verifyUnsubscribed() throws Exception {
        reset(bluetoothGatt);
        reset(descriptor);
        // {@link reset} not only resets interactions, but also stubbing, which we need to resetup.
        setUpGatt();
        callSubscribeMethod(new BleRpcController());
        onSubscribe(descriptor);
        // If unsubscribed, next call will initiate subscription.
        verifySubscribe(descriptor);
    }

    void finishConnecting() {
        onConnectionStateChange(BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);
        onServicesDiscovered(BluetoothGatt.GATT_SUCCESS);
    }

    void finishSubscribing(BluetoothGattDescriptor descriptor) {
        finishConnecting();
        onSubscribe(descriptor);
    }

    void onCharacteristicReadFail() {
        onCharacteristicRead(TEST_STATUS_NOT_SUCCESS);
    }

    void onCharacteristicRead() {
        onCharacteristicRead(BluetoothGatt.GATT_SUCCESS);
    }

    void onCharacteristicRead(int status) {
        verify(bluetoothGatt).readCharacteristic(characteristic);
        bluetoothCallback.getValue().onCharacteristicRead(bluetoothGatt, characteristic, status);
    }

    void onCharacteristicWriteFail(BluetoothGattCharacteristic characteristic) {
        onCharacteristicWrite(characteristic, TEST_STATUS_NOT_SUCCESS);
    }

    void onCharacteristicWrite(BluetoothGattCharacteristic characteristic) {
        onCharacteristicWrite(characteristic, BluetoothGatt.GATT_SUCCESS);
    }

    void onCharacteristicWrite(BluetoothGattCharacteristic characteristic, int status) {
        verify(bluetoothGatt).writeCharacteristic(characteristic);
        bluetoothCallback.getValue().onCharacteristicWrite(bluetoothGatt, characteristic, status);
    }

    void onSubscribeFail(BluetoothGattDescriptor descriptor) {
        onSubscribe(descriptor, TEST_STATUS_NOT_SUCCESS);
    }

    void onSubscribe(BluetoothGattDescriptor descriptor) {
        onSubscribe(descriptor, BluetoothGatt.GATT_SUCCESS);
    }

    void onSubscribe(BluetoothGattDescriptor descriptor, int status) {
        verifySubscribe(descriptor);
        when(descriptor.getValue()).thenReturn(TEST_ENABLE_NOTIFICATION_VALUE);
        bluetoothCallback.getValue().onDescriptorWrite(bluetoothGatt, descriptor, status);
    }

    void onUnsubscribeFail(BluetoothGattDescriptor descriptor) {
        onUnsubscribe(descriptor, TEST_STATUS_NOT_SUCCESS);
    }

    void onUnsubscribe(BluetoothGattDescriptor descriptor) {
        onUnsubscribe(descriptor, BluetoothGatt.GATT_SUCCESS);
    }

    void onUnsubscribe(BluetoothGattDescriptor descriptor, int status) {
        verifyUnsubscribe(descriptor);
        when(descriptor.getValue()).thenReturn(TEST_DISABLE_NOTIFICATION_VALUE);
        bluetoothCallback.getValue().onDescriptorWrite(bluetoothGatt, descriptor, status);
    }

    void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        bluetoothCallback.getValue().onCharacteristicChanged(bluetoothGatt, characteristic);
    }

    void onServicesDiscovered(int status) {
        verify(bluetoothGatt).discoverServices();
        bluetoothCallback.getValue().onServicesDiscovered(bluetoothGatt, status);
    }

    void onConnectionStateChange(int status, int state) {
        verify(bluetoothDevice).connectGatt(eq(context), anyBoolean(), any());
        bluetoothCallback.getValue().onConnectionStateChange(bluetoothGatt, status, state);
    }
}
