package com.blerpc;

import static com.google.common.base.Preconditions.checkArgument;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import com.blerpc.proto.Blerpc;
import com.blerpc.proto.MethodType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcChannel;
import com.google.protobuf.RpcController;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * An {@link RpcChannel} that uses BLE as a transport. Is meant to be used with default generated code for Java
 * Protobuf services.
 */
public class BleRpcChannel implements RpcChannel {

  private final BluetoothDevice bluetoothDevice;
  private final MessageConverter messageConverter;
  private final Context context;
  private final Handler workHandler;
  private final Handler listenerHandler;
  private final Logger logger;

  private ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;
  private Optional<BluetoothGatt> bluetoothGatt = Optional.absent();

  private boolean callInProgress = false;
  private final LinkedList<RpcCall> calls = new LinkedList<RpcCall>();
  private final Map<UUID, SubscriptionCallsGroup> subscriptions = new HashMap<>();

  // BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE and BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE are null in tests,
  // these variables are here for the purpuse of setting them in tests to real values.
  @SuppressWarnings("ConstantField")
  @VisibleForTesting
  static byte[] ENABLE_NOTIFICATION_VALUE = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
  @VisibleForTesting
  @SuppressWarnings("ConstantField")
  static byte[] DISABLE_NOTIFICATION_VALUE = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;

  /**
   * Create a {@link BleRpcChannel}.
   *
   * @param bluetoothDevice  a {@link BluetoothDevice} to connect to.
   * @param context          an application context.
   * @param messageConverter a {@link MessageConverter} for serializing requests and deserializing responses.
   * @param workHandler      a handler to run all channel's code.
   * @param listenerHandler  a handler run rpc callbacks.
   * @param logger           a loggen for debug logging.
   */
  public BleRpcChannel(
      BluetoothDevice bluetoothDevice,
      Context context,
      MessageConverter messageConverter,
      Handler workHandler,
      Handler listenerHandler,
      Logger logger
  ) {
    this.bluetoothDevice = bluetoothDevice;
    this.messageConverter = messageConverter;
    this.context = context;
    this.workHandler = workHandler;
    this.listenerHandler = listenerHandler;
    this.logger = logger;
  }

  @Override
  public void callMethod(
      MethodDescriptor method,
      RpcController controller,
      Message request,
      Message responsePrototype,
      RpcCallback<Message> done
  ) {
    workHandler.post(() -> {
      RpcCall rpcCall = new RpcCall(method, controller, request, responsePrototype, done);
      if (!checkMethodType(rpcCall)) {
        return;
      }

      addCall(rpcCall);
      switch (connectionStatus) {
        case DISCONNECTED:
          startConnection();
          break;
        case CONNECTING:
          break;
        case CONNECTED:
          startNextCallIfNotInProgress();
          break;
      }
    });
  }

  private void addCall(RpcCall rpcCall) {
    calls.add(rpcCall);
    if (rpcCall.getMethodType() != MethodType.SUBSCRIBE) {
      return;
    }

    UUID characteristic = rpcCall.getCharacteristic();
    SubscriptionCallsGroup subscription;
    if (subscriptions.containsKey(characteristic)) {
      subscription = subscriptions.get(characteristic);
    } else {
      subscription = new SubscriptionCallsGroup(rpcCall.getService(), characteristic, rpcCall.getDescriptor(),
          rpcCall.method, rpcCall.responsePrototype);
      subscriptions.put(characteristic, subscription);
    }
    subscription.calls.add(rpcCall);
  }

  private boolean checkMethodType(RpcCall rpcCall) {
    MethodType methodType = rpcCall.getMethodType();
    switch (methodType) {
      case READ:
      case WRITE:
      case SUBSCRIBE: {
        return true;
      }
      default: {
        notifyCallFailed(rpcCall, "Unsupported method type %s.", methodType);
        return false;
      }
    }
  }

  private void startConnection() {
    connectionStatus = ConnectionStatus.CONNECTING;
    bluetoothGatt = Optional.fromNullable(bluetoothDevice.connectGatt(context, /*autoConnect=*/ false, gattCallback));
    if (!bluetoothGatt.isPresent()) {
      failAllAndReset("Could not get bluetooth gatt.");
    }
  }

  private void handleResult(byte[] value) {
    RpcCall currentCall = finishRpcCall();
    try {
      Message response = messageConverter.deserializeResponse(currentCall.method, currentCall.responsePrototype, value);
      notifyResultForCall(currentCall, response);
    } catch (CouldNotConvertMessageException exception) {
      notifyCallFailed(currentCall, exception.getMessage());
    }
    startNextCall();
  }

  private void handleError(String format, Object... args) {
    RpcCall currentCall = finishRpcCall();
    notifyCallFailed(currentCall, format, args);
    startNextCall();
  }

  private void handleSubscribe(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
    RpcCall rpcCall = finishRpcCall();
    UUID characteristicUuid = characteristic.getUuid();
    if (Arrays.equals(value, ENABLE_NOTIFICATION_VALUE)) {
      SubscriptionCallsGroup subscription = getSubscribingSubscription(characteristicUuid);
      subscription.status = SubscriptionStatus.SUBSCRIBED;
      callOnSubscribeSuccess(rpcCall.controller);
    } else if (Arrays.equals(value, DISABLE_NOTIFICATION_VALUE)) {
      SubscriptionCallsGroup subscription = getUnsubscribingSubscription(characteristicUuid);
      subscription.status = SubscriptionStatus.UNSUBSCRIBED;
      // New rpc calls might have been added since we started unsubscribing.
      subscription.clearCanceled();
      if (!subscription.hasAnySubscriber()) {
        failIfFalse(
            gatt.setCharacteristicNotification(characteristic, /*enable=*/ false),
            "Failed to disable notification for characteristic %s in service %s.",
            rpcCall.characteristicUuid,
            rpcCall.serviceUuid);
        subscriptions.remove(subscription.characteristicUuid);
        return;
      }
    } else {
      checkArgument(false, "Unexpected value \"%s\" of the subscription state.", Arrays.toString(value));
    }
    startNextCall();
  }

  private void handleSubscribeError(BluetoothGattCharacteristic characteristic, UUID descriptorUuid, byte[] value,
                                    String format, Object... args) {
    finishRpcCall();
    UUID characteristicUuid = characteristic.getUuid();
    if (Arrays.equals(value, ENABLE_NOTIFICATION_VALUE)) {
      SubscriptionCallsGroup subscription = getSubscribingSubscription(characteristicUuid);
      failAllSubscribersAndClear(subscription,
          "Failed to enable notifications for descriptor %s in characteristic %s.",
          characteristicUuid, descriptorUuid);
      startNextCall();
    } else if (Arrays.equals(value, DISABLE_NOTIFICATION_VALUE)) {
      failAllAndReset("Failed unsubscribing from characteristic %s, descriptor %s.",
          characteristicUuid, descriptorUuid);
    } else {
      checkArgument(false, "Unexpected value \"%s\" of the subscription state.", Arrays.toString(value));
    }
  }

  private void handleValueChange(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
    UUID characteristicUuid = characteristic.getUuid();
    if (!subscriptions.containsKey(characteristicUuid)) {
      return;
    }

    SubscriptionCallsGroup subscription = getSubscription(characteristicUuid);
    if (subscription.status != SubscriptionStatus.SUBSCRIBED) {
      return;
    }

    BluetoothGattDescriptor descriptor = getDescriptor(characteristic, subscription.descriptorUuid);
    // If all calls were cancelled, abandon the subscription.
    subscription.clearCanceled();
    if (!subscription.hasAnySubscriber()) {
      startUnsubscribing(subscription);
      return;
    }

    try {
      Message response = messageConverter.deserializeResponse(subscription.method, subscription.responsePrototype, characteristic.getValue());
      for (RpcCall call : subscription.calls) {
        notifyResultForCall(call, response);
      }
    } catch (CouldNotConvertMessageException exception) {
      failAllSubscribers(subscription, exception.getMessage());
      startUnsubscribing(subscription);
    }
  }

  private SubscriptionCallsGroup getSubscribingSubscription(UUID characteristicUuid) {
    SubscriptionCallsGroup subscription = getSubscriptionWithSubscribers(characteristicUuid);
    checkArgument(subscription.status == SubscriptionStatus.SUBSCRIBING, "The characteristic %s is not subscribing.", characteristicUuid);
    return subscription;
  }

  private SubscriptionCallsGroup getUnsubscribingSubscription(UUID characteristicUuid) {
    SubscriptionCallsGroup subscription = getSubscription(characteristicUuid);
    checkArgument(subscription.status == SubscriptionStatus.UNSUBSCRIBING, "The characteristic %s is not unsubscribing.", characteristicUuid);
    return subscription;
  }

  private SubscriptionCallsGroup getSubscriptionWithSubscribers(UUID characteristicUuid) {
    SubscriptionCallsGroup subscription = getSubscription(characteristicUuid);
    checkArgument(subscription.hasAnySubscriber(),
        "There are no subscribers for characteristic %s.", characteristicUuid);
    return subscription;
  }

  private SubscriptionCallsGroup getSubscription(UUID characteristicUuid) {
    checkArgument(subscriptions.containsKey(characteristicUuid),
        "There is no subscription calls group for characteristic %s", characteristicUuid);
    return subscriptions.get(characteristicUuid);
  }

  private static long toNumber(boolean value) {
    return value ? 1 : 0;
  }

  private RpcCall finishRpcCall() {
    checkArgument(callInProgress, "There is no call in progress.");
    checkArgument(!calls.isEmpty(), "There are no RPC calls.");
    callInProgress = false;
    return calls.poll();
  }

  private void failAllSubscribers(SubscriptionCallsGroup subscription, String format, Object... args) {
    for (RpcCall rpcCall : subscription.calls) {
      notifyCallFailed(rpcCall, format, args);
    }
    subscription.calls.clear();
  }

  private void failAllSubscribersAndClear(SubscriptionCallsGroup subscription, String format, Object... args) {
    failAllSubscribers(subscription, format, args);
    subscriptions.remove(subscription.characteristicUuid);
  }

  private void failAllAndReset(String format, Object... args) {
    FluentIterable<RpcCall> callsToNotify = FluentIterable.from(calls)
        .filter(rpcCall -> !rpcCall.isUnsubscribeCall)
        .filter(rpcCall -> !skipFailedCall(rpcCall));
    for (RpcCall call : callsToNotify) {
      notifyCallFailed(call, format, args);
    }
    for (RpcCall call : Sets.difference(allSubscriptionCalls(), ImmutableSet.copyOf(calls))) {
      notifyCallFailed(call, format, args);
    }
    reset();
  }

  private Set<RpcCall> allSubscriptionCalls() {
    return FluentIterable.from(subscriptions.values()).transformAndConcat(callsGroup -> callsGroup.calls).toSet();
  }

  protected void reset() {
    connectionStatus = ConnectionStatus.DISCONNECTED;
    callInProgress = false;
    calls.clear();
    subscriptions.clear();
    if (bluetoothGatt.isPresent()) {
      bluetoothGatt.get().close();
      bluetoothGatt = Optional.absent();
    }
  }

  private void startNextCallIfNotInProgress() {
    if (callInProgress) {
      return;
    }
    startNextCall();
  }

  private void startNextCall() {
    boolean callStarted = false;
    while (!calls.isEmpty() && !callStarted) {
      RpcCall rpcCall = calls.peek();
      BluetoothGatt gatt = bluetoothGatt.get();
      if (!rpcCall.isUnsubscribeCall) {
        if (skipFailedCall(rpcCall)
            || skipCancelledCall(rpcCall)
            || skipSubscriptionNotNeeded(rpcCall)
            || !checkRpcCallMethod(gatt, rpcCall)
            || !checkCharacteristicParams(gatt, rpcCall)) {
          calls.poll();
          continue;
        }
      }

      switch (rpcCall.getMethodType()) {
        case READ:
        case WRITE:
          callStarted = startNextReadWriteCall(gatt, rpcCall);
          break;
        case SUBSCRIBE:
          callStarted = rpcCall.isUnsubscribeCall
              ? startNextUnsubscribeCall(gatt, rpcCall)
              : startNextSubscribeCall(gatt, rpcCall);
          break;
        default:
      }
    }
  }

  private static boolean skipFailedCall(RpcCall rpcCall) {
    if (rpcCall.controller.failed()) {
      checkArgument(rpcCall.getMethodType() == MethodType.SUBSCRIBE,
          "Only SUBSCRIBE method calls can be failed while in the call queue.");
      return true;
    }
    return false;
  }

  private boolean skipCancelledCall(RpcCall rpcCall) {
    if (rpcCall.controller.isCanceled()) {
      if (rpcCall.getMethodType() != MethodType.SUBSCRIBE) {
        notifyDefaultResultForCall(rpcCall);
      }
      return true;
    }
    return false;
  }

  private boolean skipSubscriptionNotNeeded(RpcCall rpcCall) {
    if (rpcCall.getMethodType() != MethodType.SUBSCRIBE) {
      return false;
    }

    SubscriptionCallsGroup subscription = getSubscription(rpcCall.getCharacteristic());
    if (subscription.status == SubscriptionStatus.SUBSCRIBED) {
      callOnSubscribeSuccess(rpcCall.controller);
    }
    if (subscription.status != SubscriptionStatus.UNSUBSCRIBED) {
      return true;
    }
    subscription.clearCanceled();
    return !subscription.hasAnySubscriber();
  }

  private void callOnSubscribeSuccess(RpcController controller) {
    if (controller instanceof BleRpcController) {
      ((BleRpcController) controller).onSubscribeSuccess();
    }
  }

  private boolean checkRpcCallMethod(BluetoothGatt bluetoothGatt, RpcCall rpcCall) {
    UUID serviceId = rpcCall.getService();
    UUID characteristicId = rpcCall.getCharacteristic();
    BluetoothGattService service = bluetoothGatt.getService(serviceId);
    if (service == null) {
      notifyCallFailed(rpcCall, "Device does not have service %s.", serviceId);
      return false;
    }

    BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicId);
    if (characteristic == null) {
      notifyCallFailed(rpcCall, "Service %s does not have characteristic %s.", serviceId, characteristicId);
      return false;
    }

    if (rpcCall.getMethodType() == MethodType.SUBSCRIBE) {
      BluetoothGattDescriptor descriptor = characteristic.getDescriptor(rpcCall.getDescriptor());
      if (descriptor == null) {
        notifyCallFailed(rpcCall, "Characteristic %s in service service %s does not have descriptor %s.",
            characteristicId, serviceId, rpcCall.getDescriptor());
        return false;
      }
    }
    return true;
  }

  private boolean checkCharacteristicParams(BluetoothGatt bluetoothGatt, RpcCall rpcCall) {
    UUID serviceId = rpcCall.getService();
    UUID characteristicId = rpcCall.getCharacteristic();
    BluetoothGattService service = bluetoothGatt.getService(serviceId);
    BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicId);
    MethodType methodType = rpcCall.getMethodType();
    switch (methodType) {
      case READ: {
        if (!isReadable(characteristic)) {
          notifyCallFailed(rpcCall, "Characteristic %s on service %s is not readable.",
              characteristicId, serviceId);
          return false;
        }
        break;
      }
      case WRITE: {
        if (!isWritable(characteristic)) {
          notifyCallFailed(rpcCall, "Characteristic %s on service %s is not writable.",
              characteristicId, serviceId);
          return false;
        }
        break;
      }
      case SUBSCRIBE: {
        if (!isNotifiable(characteristic)) {
          notifyCallFailed(rpcCall, "Characteristic %s on service %s is not notifiable.",
              characteristicId, serviceId);
          return false;
        }
        break;
      }
      default:
    }
    return true;
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

  private boolean startNextReadWriteCall(BluetoothGatt bluetoothGatt, RpcCall rpcCall) {
    callInProgress = true;
    try {
      makeRequest(bluetoothGatt, rpcCall);
      return true;
    } catch (CouldNotConvertMessageException | MakeRequestException exception) {
      calls.poll();
      callInProgress = false;
      notifyCallFailed(rpcCall, exception.getMessage());
      return false;
    }
  }

  private boolean startNextSubscribeCall(BluetoothGatt bluetoothGatt, RpcCall rpcCall) {
    SubscriptionCallsGroup subscription = getSubscription(rpcCall.getCharacteristic());
    callInProgress = true;
    subscription.status = SubscriptionStatus.SUBSCRIBING;
    try {
      makeSubscribeRequest(bluetoothGatt, rpcCall);
      return true;
    } catch (MakeRequestException exception) {
      calls.poll();
      subscription.status = SubscriptionStatus.UNSUBSCRIBED;
      callInProgress = false;
      failAllSubscribersAndClear(subscription, exception.getMessage());
      return false;
    }
  }

  private boolean startNextUnsubscribeCall(BluetoothGatt bluetoothGatt, RpcCall rpcCall) {
    getUnsubscribingSubscription(rpcCall.getCharacteristic());
    BluetoothGattCharacteristic characteristic = getCharacteristic(bluetoothGatt, rpcCall);
    BluetoothGattDescriptor descriptor = getDescriptor(characteristic, rpcCall.getDescriptor());
    callInProgress = true;
    makeUnsubscribeRequest(bluetoothGatt, rpcCall.getCharacteristic(), descriptor);
    return true;
  }

  private void makeRequest(BluetoothGatt bluetoothGatt, RpcCall rpcCall)
      throws CouldNotConvertMessageException {
    BluetoothGattCharacteristic characteristic = getCharacteristic(bluetoothGatt, rpcCall);
    switch (rpcCall.getMethodType()) {
      case READ: {
        makeReadRequest(bluetoothGatt, characteristic);
        break;
      }
      case WRITE: {
        makeWriteRequest(bluetoothGatt, characteristic, rpcCall);
        break;
      }
      default: {
        checkArgument(false, "Unsupported method type %s.", rpcCall.getMethodType());
      }
    }
  }

  private static void makeReadRequest(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic) {
    failIfFalse(bluetoothGatt.readCharacteristic(characteristic), "Failed to read characteristic %s.",
        characteristic.getUuid());
  }

  private void makeWriteRequest(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic,
                                RpcCall rpcCall) throws CouldNotConvertMessageException {
    byte[] value = messageConverter.serializeRequest(rpcCall.method, rpcCall.request);
    setCharacteristicValue(characteristic, value);
    failIfFalse(bluetoothGatt.writeCharacteristic(characteristic), "Failed to write characteristic %s.",
        characteristic.getUuid());
  }

  private static void makeSubscribeRequest(BluetoothGatt bluetoothGatt, RpcCall rpcCall) {
    UUID serviceId = rpcCall.getService();
    UUID characteristicId = rpcCall.getCharacteristic();
    UUID descriptorId = rpcCall.getDescriptor();
    BluetoothGattCharacteristic characteristic = getCharacteristic(bluetoothGatt, rpcCall);
    BluetoothGattDescriptor descriptor = getDescriptor(characteristic, rpcCall);
    failIfFalse(bluetoothGatt.setCharacteristicNotification(characteristic, true),
        "Failed to enable notification for characteristic %s in service %s.", characteristicId, serviceId);
    setDescriptorValue(descriptor, ENABLE_NOTIFICATION_VALUE);
    failIfFalse(bluetoothGatt.writeDescriptor(descriptor),
        "Failed to write the descriptor %s in characteristic %s in service %s.",
        descriptorId, characteristicId, serviceId);
  }

  private void startUnsubscribing(SubscriptionCallsGroup subscription) {
    subscription.status = SubscriptionStatus.UNSUBSCRIBING;
    calls.add(RpcCall.unsubscribeCall(subscription.serviceUuid, subscription.characteristicUuid,
        subscription.descriptorUuid));
    startNextCallIfNotInProgress();
  }

  private void makeUnsubscribeRequest(BluetoothGatt bluetoothGatt, UUID characteristicUuid,
                                      BluetoothGattDescriptor descriptor) {
    setDescriptorValue(descriptor, DISABLE_NOTIFICATION_VALUE);
    if (!bluetoothGatt.writeDescriptor(descriptor)) {
      failAllAndReset("Failed unsubscribing from characteristic %s, descriptor %s.",
          characteristicUuid, descriptor.getUuid());
    }
  }

  private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int state) {
      workHandler.post(() -> {
        if (state != BluetoothProfile.STATE_CONNECTED && state != BluetoothProfile.STATE_DISCONNECTED) {
          logger.info(String.format("Unexpected connection state: state=%d, status=%d.", state, status));
          return;
        }

        boolean success =
            state == BluetoothProfile.STATE_CONNECTED
                && status == BluetoothGatt.GATT_SUCCESS;
        if (!success) {
          failAllAndReset("Could not connect: state=%d, status=%d.", state, status);
          return;
        }

        if (!gatt.discoverServices()) {
          failAllAndReset("Could not start service discovery.");
          return;
        }
      });
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
      workHandler.post(() -> {
        if (status != BluetoothGatt.GATT_SUCCESS) {
          failAllAndReset("Services discovery failed, status=%d.", status);
          return;
        }

        connectionStatus = ConnectionStatus.CONNECTED;
        startNextCall();
      });
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
      workHandler.post(() -> {
        if (status != BluetoothGatt.GATT_SUCCESS) {
          handleError("Failed to read characteristic %s: status=%d.", characteristic.getUuid(), status);
        } else {
          handleResult(characteristic.getValue());
        }
      });
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
      workHandler.post(() -> {
        if (status != BluetoothGatt.GATT_SUCCESS) {
          handleError("Failed to write characteristic %s: status=%d.", characteristic.getUuid(), status);
        } else {
          handleResult(characteristic.getValue());
        }
      });
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
      workHandler.post(() -> {
        BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
        if (status != BluetoothGatt.GATT_SUCCESS) {
          handleSubscribeError(characteristic, descriptor.getUuid(), descriptor.getValue(),
              "Failed to subscribe to descriptor %s: status=%d.", descriptor.getUuid(), status);
        } else {
          handleSubscribe(gatt, characteristic, descriptor.getValue());
        }
      });
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
      workHandler.post(() -> handleValueChange(gatt, characteristic));
    }
  };

  private void notifyCallFailed(RpcCall rpcCall, String format, Object... args) {
    rpcCall.controller.setFailed(String.format(format, args));
    callCallback(rpcCall, rpcCall.responsePrototype.getDefaultInstanceForType());
  }

  private void notifyDefaultResultForCall(RpcCall rpcCall) {
    notifyResultForCall(rpcCall, rpcCall.responsePrototype.getDefaultInstanceForType());
  }

  private void notifyResultForCall(RpcCall rpcCall, Message message) {
    callCallback(rpcCall, message);
  }

  private void callCallback(RpcCall rpcCall, Message message) {
    // There is no check on canceling call, because call might get canceled after
    // isCanceled returned false and before the callback is called, the probability of that
    // is extremely low, but nothing can be done about it. To prevent this rear case, callback
    // will be called independently on canceling and value after cancel should be ignored by
    // class that implement callback.
    listenerHandler.post(() -> rpcCall.done.run(message));
  }

  private static BluetoothGattService getService(BluetoothGatt gatt, UUID serviceUuid) {
    BluetoothGattService service = gatt.getService(serviceUuid);
    checkArgument(service != null, "Device does not have service %s.", serviceUuid);
    return service;
  }

  private static BluetoothGattCharacteristic getCharacteristic(BluetoothGatt gatt, RpcCall rpcCall) {
    BluetoothGattService service = getService(gatt, rpcCall.getService());
    BluetoothGattCharacteristic characteristic = service.getCharacteristic(rpcCall.getCharacteristic());
    checkArgument(characteristic != null, "Service %s does not have characteristic %s.", rpcCall.getService(),
        rpcCall.getCharacteristic());
    return characteristic;
  }

  private static BluetoothGattDescriptor getDescriptor(BluetoothGattCharacteristic characteristic, RpcCall rpcCall) {
    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(rpcCall.getDescriptor());
    checkArgument(descriptor != null, "Characteristic %s in service %s does not have descriptor %s.",
        rpcCall.getCharacteristic(), rpcCall.getService(), rpcCall.getDescriptor());
    return descriptor;
  }

  private static BluetoothGattDescriptor getDescriptor(BluetoothGattCharacteristic characteristic, UUID descriptorUuid) {
    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUuid);
    checkArgument(descriptor != null, "Characteristic %s does not have descriptor %s.",
        characteristic.getUuid(), descriptorUuid);
    return descriptor;
  }

  private static void setCharacteristicValue(BluetoothGattCharacteristic characteristic, byte[] value) {
    // characteristic.setValue always returns true (as of the date this code was written).
    checkArgument(characteristic.setValue(value), "Failed to set value \"%s\" for characteristic %s.",
        Arrays.toString(value), characteristic.getUuid());
  }

  private static void setDescriptorValue(BluetoothGattDescriptor descriptor, byte[] value) {
    // descriptor.setValue always returns true (as of the date this code was written).
    checkArgument(descriptor.setValue(value), "Failed to set value \"%s\" for descriptor %s.",
        Arrays.toString(value), descriptor.getUuid());
  }

  /**
   * The {@link checkArgument} is used as an assert to ckeck for impossible conditions and catch bugs.
   * This method to be used for checking success in normal execution instead.
   */
  private static void failIfFalse(boolean value, String format, Object... args) {
    if (!value) {
      throw new MakeRequestException(format, args);
    }
  }

  private static class RpcCall {
    private final MethodDescriptor method;
    private final RpcController controller;
    private final Message request;
    private final Message responsePrototype;
    private final RpcCallback<Message> done;
    private final boolean isUnsubscribeCall;
    private final UUID serviceUuid;
    private final UUID characteristicUuid;
    private final UUID descriptorUuid;

    // Create normal RpcCall.
    RpcCall(MethodDescriptor method, RpcController controller, Message request, Message responsePrototype,
            RpcCallback<Message> done) {
      this.method = method;
      this.controller = controller;
      this.request = request;
      this.responsePrototype = responsePrototype;
      this.done = done;
      this.isUnsubscribeCall = false;
      this.serviceUuid = null;
      this.characteristicUuid = null;
      this.descriptorUuid = null;
    }

    // Create fake RpcCall for unsubscribing.
    RpcCall(UUID serviceUuid, UUID characteristicUuid, UUID descriptorUuid) {
      this.method = null;
      this.controller = null;
      this.request = null;
      this.responsePrototype = null;
      this.done = null;
      this.isUnsubscribeCall = true;
      this.serviceUuid = serviceUuid;
      this.characteristicUuid = characteristicUuid;
      this.descriptorUuid = descriptorUuid;
    }

    static RpcCall unsubscribeCall(UUID serviceUuid, UUID characteristicUuid, UUID descriptorUuid) {
      return new RpcCall(serviceUuid, characteristicUuid, descriptorUuid);
    }

    UUID getService() {
      if (isUnsubscribeCall) {
        return serviceUuid;
      }
      return UUID.fromString(method.getService().getOptions().getExtension(Blerpc.service).getUuid());
    }

    UUID getCharacteristic() {
      if (isUnsubscribeCall) {
        return characteristicUuid;
      }
      return UUID.fromString(method.getOptions().getExtension(Blerpc.characteristic).getUuid());
    }

    UUID getDescriptor() {
      if (isUnsubscribeCall) {
        return descriptorUuid;
      }
      return UUID.fromString(method.getOptions().getExtension(Blerpc.characteristic).getDescriptorUuid());
    }

    MethodType getMethodType() {
      if (isUnsubscribeCall) {
        return MethodType.SUBSCRIBE;
      }
      return method.getOptions().getExtension(Blerpc.characteristic).getType();
    }
  }

  private static class SubscriptionCallsGroup {
    private final UUID serviceUuid;
    private final UUID characteristicUuid;
    private final UUID descriptorUuid;
    private final Set<RpcCall> calls = new HashSet<>();
    private SubscriptionStatus status = SubscriptionStatus.UNSUBSCRIBED;
    private final MethodDescriptor method;
    private final Message responsePrototype;

    private SubscriptionCallsGroup(UUID serviceUuid, UUID characteristicUuid, UUID descriptorUuid,
                                   MethodDescriptor method, Message responsePrototype) {
      this.serviceUuid = serviceUuid;
      this.characteristicUuid = characteristicUuid;
      this.descriptorUuid = descriptorUuid;
      this.method = method;
      this.responsePrototype = responsePrototype;
    }

    void clearCanceled() {
      calls.removeAll(canceledSubscribers());
    }

    boolean hasAnySubscriber() {
      return !calls.isEmpty();
    }

    private Set<RpcCall> canceledSubscribers() {
      return FluentIterable.from(calls)
          .filter(call -> call.controller.isCanceled())
          .toSet();
    }
  }

  /**
   * An exception to be thrown when making a read/write request has failed.
   */
  private static class MakeRequestException extends RuntimeException {
    public MakeRequestException(String format, Object... args) {
      super(String.format(format, args));
    }
  }

  private enum ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
  }

  private enum SubscriptionStatus {
    UNSUBSCRIBED,
    SUBSCRIBING,
    SUBSCRIBED,
    UNSUBSCRIBING
  }
}
