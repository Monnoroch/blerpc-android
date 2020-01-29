package com.blerpc;

import static com.google.common.base.Preconditions.checkArgument;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
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
    checkArgument(controller instanceof BleRpcController, "Invalid RpcController instance.");
    workHandler.post(() -> {
      // TODO: move validation outside handler.
      RpcCall rpcCall = new RpcCall(method, (BleRpcController) controller, request, responsePrototype, done);
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

  private void addCall(RpcCall rpcCall) {
    calls.add(rpcCall);
    if (rpcCall.getMethodType().equals(MethodType.SUBSCRIBE)) {
      getSubscriptionForCall(rpcCall).calls.add(rpcCall);
    }
  }

  private SubscriptionCallsGroup getSubscriptionForCall(RpcCall rpcCall) {
    UUID characteristic = rpcCall.getCharacteristic();
    if (subscriptions.containsKey(characteristic)) {
      return subscriptions.get(characteristic);
    } else {
      SubscriptionCallsGroup subscription = new SubscriptionCallsGroup(rpcCall.getService(), characteristic,
          rpcCall.getDescriptor(), rpcCall.method, rpcCall.responsePrototype);
      subscriptions.put(characteristic, subscription);
      return subscription;
    }
  }

  private void startConnection() {
    connectionStatus = ConnectionStatus.CONNECTING;
    bluetoothGatt = Optional.fromNullable(bluetoothDevice.connectGatt(context, /*autoConnect=*/ false, gattCallback));
    if (!bluetoothGatt.isPresent()) {
      failAllAndReset("Could not get bluetooth gatt.");
    }
  }

  private void startNextCallIfNotInProgress() {
    if (!callInProgress) {
      startNextCall();
    }
  }

  private void startNextCall() {
    while (!calls.isEmpty()) {
      // `bluetoothGatt` is `absent()` only when `calls.isEmpty()`.
      BluetoothGatt gatt = bluetoothGatt.get();
      if (tryStartNextCall(gatt)) {
        return;
      }
    }
  }

  private boolean tryStartNextCall(BluetoothGatt gatt) {
    RpcCall rpcCall = calls.peek();
    if (rpcCall.isUnsubscribeCall) {
      return startNextUnsubscribeCall(gatt, rpcCall);
    }

    if (!validateCharacteristic(gatt, rpcCall)) {
      calls.poll();
      return false;
    }

    if (skipCall(gatt, rpcCall)) {
      calls.poll();
      return false;
    }

    switch (rpcCall.getMethodType()) {
      case READ:
      case WRITE:
        return startNextReadWriteCall(gatt, rpcCall);
      case SUBSCRIBE:
        return startNextSubscribeCall(gatt, rpcCall);
      default:
        return false;
    }
  }

  private boolean validateCharacteristic(BluetoothGatt gatt, RpcCall rpcCall) {
    try {
      Characteristics.validate(gatt, rpcCall.getService(), rpcCall.getCharacteristic(), rpcCall.getDescriptor(),
          rpcCall.getMethodType());
      return true;
    } catch (Characteristics.BleValidationException exception) {
      notifyCallFailed(rpcCall, exception.getMessage());
      return false;
    }
  }

  private boolean skipCall(BluetoothGatt gatt, RpcCall rpcCall) {
    return skipFailedCall(rpcCall)
        || skipCancelledCall(rpcCall)
        || skipSubscriptionNotNeeded(rpcCall);
  }

  private static boolean skipFailedCall(RpcCall rpcCall) {
    if (rpcCall.controller.failed()) {
      // Because we always make sure to remove the call from the queue before failing it.
      checkArgument(rpcCall.getMethodType().equals(MethodType.SUBSCRIBE),
          "Only SUBSCRIBE method calls can be failed while in the call queue.");
      return true;
    }
    return false;
  }

  private boolean skipCancelledCall(RpcCall rpcCall) {
    if (!rpcCall.controller.isCanceled()) {
      return false;
    }
    if (!rpcCall.getMethodType().equals(MethodType.SUBSCRIBE)) {
      notifyDefaultResultForCall(rpcCall);
    }
    return true;
  }

  private boolean skipSubscriptionNotNeeded(RpcCall rpcCall) {
    if (!rpcCall.getMethodType().equals(MethodType.SUBSCRIBE)) {
      return false;
    }

    SubscriptionCallsGroup subscription = getSubscription(rpcCall.getCharacteristic());
    if (subscription.status.equals(SubscriptionStatus.SUBSCRIBED)) {
      rpcCall.controller.onSubscribeSuccess();
    }
    if (!subscription.status.equals(SubscriptionStatus.UNSUBSCRIBED)) {
      return true;
    }
    subscription.clearCanceled();
    return !subscription.hasAnySubscriber();
  }

  private boolean startNextReadWriteCall(BluetoothGatt bluetoothGatt, RpcCall rpcCall) {
    callInProgress = true;
    try {
      makeRequest(bluetoothGatt, rpcCall);
      return true;
    } catch (CouldNotConvertMessageException | Characteristics.BleApiException exception) {
      finishRpcCall();
      notifyCallFailed(rpcCall, exception.getMessage());
      return false;
    }
  }

  private void makeRequest(BluetoothGatt bluetoothGatt, RpcCall rpcCall)
      throws CouldNotConvertMessageException, Characteristics.BleApiException {
    switch (rpcCall.getMethodType()) {
      case READ: {
        makeReadRequest(bluetoothGatt, rpcCall);
        break;
      }
      case WRITE: {
        makeWriteRequest(bluetoothGatt, rpcCall);
        break;
      }
      default:
        break;
    }
  }

  private static void makeReadRequest(BluetoothGatt bluetoothGatt, RpcCall rpcCall)
      throws Characteristics.BleApiException {
    Characteristics.readValue(bluetoothGatt, rpcCall.getService(), rpcCall.getCharacteristic());
  }

  private void makeWriteRequest(BluetoothGatt bluetoothGatt, RpcCall rpcCall)
      throws CouldNotConvertMessageException, Characteristics.BleApiException {
    byte[] value = messageConverter.serializeRequest(rpcCall.method, rpcCall.request);
    Characteristics.writeValue(bluetoothGatt, rpcCall.getService(), rpcCall.getCharacteristic(), value);
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

  private boolean startNextSubscribeCall(BluetoothGatt bluetoothGatt, RpcCall rpcCall) {
    SubscriptionCallsGroup subscription = getSubscription(rpcCall.getCharacteristic());
    callInProgress = true;
    subscription.status = SubscriptionStatus.SUBSCRIBING;
    try {
      makeSubscribeRequest(bluetoothGatt, rpcCall);
      return true;
    } catch (Characteristics.BleApiException exception) {
      subscription.status = SubscriptionStatus.UNSUBSCRIBED;
      finishRpcCall();
      failAllSubscribersAndClear(subscription, exception.getMessage());
      return false;
    }
  }

  private static void makeSubscribeRequest(BluetoothGatt bluetoothGatt, RpcCall rpcCall)
      throws Characteristics.BleApiException {
    Characteristics.setNotification(bluetoothGatt, rpcCall.getService(), rpcCall.getCharacteristic(),
        /* enabled= */ true);
    Characteristics.writeDescriptorValue(
        bluetoothGatt,
        rpcCall.getService(),
        rpcCall.getCharacteristic(),
        rpcCall.getDescriptor(),
        ENABLE_NOTIFICATION_VALUE);
  }

  private void handleSubscribed(int status) {
    if (status == BluetoothGatt.GATT_SUCCESS) {
      handleSubscribedSuccess();
    } else {
      handleSubscribedError(status);
    }
  }

  private void handleSubscribedSuccess() {
    RpcCall rpcCall = finishRpcCall();
    SubscriptionCallsGroup subscription = getSubscribingSubscription(rpcCall.getCharacteristic());
    subscription.status = SubscriptionStatus.SUBSCRIBED;
    rpcCall.controller.onSubscribeSuccess();
    startNextCall();
  }

  private void handleSubscribedError(int status) {
    RpcCall rpcCall = finishRpcCall();
    UUID characteristicUuid = rpcCall.getCharacteristic();
    SubscriptionCallsGroup subscription = getSubscribingSubscription(characteristicUuid);
    failAllSubscribersAndClear(subscription,
        "Failed to subscribe to descriptor %s in characteristic %s in service %s with status %d.",
        rpcCall.getDescriptor(), characteristicUuid, rpcCall.getService(), status);
    startNextCall();
  }

  private void startUnsubscribing(SubscriptionCallsGroup subscription) {
    subscription.status = SubscriptionStatus.UNSUBSCRIBING;
    calls.add(RpcCall.unsubscribeCall(subscription.serviceUuid, subscription.characteristicUuid,
        subscription.descriptorUuid));
    startNextCallIfNotInProgress();
  }

  private boolean startNextUnsubscribeCall(BluetoothGatt bluetoothGatt, RpcCall rpcCall) {
    getUnsubscribingSubscription(rpcCall.getCharacteristic());
    callInProgress = true;
    makeUnsubscribeRequest(bluetoothGatt, rpcCall);
    return true;
  }

  private void makeUnsubscribeRequest(BluetoothGatt bluetoothGatt, RpcCall rpcCall) {
    try {
      Characteristics.writeDescriptorValue(
          bluetoothGatt,
          rpcCall.getService(),
          rpcCall.getCharacteristic(),
          rpcCall.getDescriptor(),
          DISABLE_NOTIFICATION_VALUE);
    } catch (Characteristics.BleApiException exception) {
      failAllAndReset(exception.getMessage());
    }
  }

  private void handleUnsubscribed(int status) {
    if (status == BluetoothGatt.GATT_SUCCESS) {
      handleUnsubscribedSuccess();
    } else {
      handleUnsubscribedError(status);
    }
  }

  private void handleUnsubscribedSuccess() {
    RpcCall rpcCall = finishRpcCall();
    SubscriptionCallsGroup subscription = getUnsubscribingSubscription(rpcCall.getCharacteristic());
    subscription.status = SubscriptionStatus.UNSUBSCRIBED;
    // New rpc calls might have been added since we started unsubscribing.
    subscription.clearCanceled();
    if (!subscription.hasAnySubscriber()) {
      subscriptions.remove(subscription.characteristicUuid);
      return;
    }
    startNextCall();
  }

  private void handleUnsubscribedError(int status) {
    RpcCall rpcCall = finishRpcCall();
    failAllAndReset("Failed unsubscribing from descriptor %s in characteristic %s in service %s with status %d.",
        rpcCall.getDescriptor(), rpcCall.getCharacteristic(), rpcCall.getService(), status);
  }

  private void handleValueChange(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
    UUID characteristicUuid = characteristic.getUuid();
    if (!subscriptions.containsKey(characteristicUuid)) {
      // Just skip unwanted values.
      return;
    }

    SubscriptionCallsGroup subscription = getSubscription(characteristicUuid);
    if (!subscription.status.equals(SubscriptionStatus.SUBSCRIBED)) {
      return;
    }

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
    checkArgument(subscription.status.equals(SubscriptionStatus.SUBSCRIBING), "The characteristic %s is not subscribing.", characteristicUuid);
    return subscription;
  }

  private SubscriptionCallsGroup getUnsubscribingSubscription(UUID characteristicUuid) {
    SubscriptionCallsGroup subscription = getSubscription(characteristicUuid);
    checkArgument(subscription.status.equals(SubscriptionStatus.UNSUBSCRIBING), "The characteristic %s is not unsubscribing.", characteristicUuid);
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

  private RpcCall finishRpcCall() {
    checkArgument(callInProgress, "There is no call in progress.");
    checkArgument(!calls.isEmpty(), "There are no RPC calls.");
    callInProgress = false;
    return calls.poll();
  }

  private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int state) {
      workHandler.post(() -> {
        if (state != BluetoothProfile.STATE_CONNECTED && state != BluetoothProfile.STATE_DISCONNECTED) {
          logger.info(String.format("Unexpected connection state: state=%d, status=%d.", state, status));
          // Skip other statuses.
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
        if (Arrays.equals(value, ENABLE_NOTIFICATION_VALUE)) {
          handleSubscribed(status);
        } else if (Arrays.equals(value, DISABLE_NOTIFICATION_VALUE)) {
          handleUnsubscribed(status);
        } else {
          checkArgument(false, "Unexpected value \"%s\" of the subscription state.", Arrays.toString(value));
        }
      });
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
      workHandler.post(() -> handleValueChange(gatt, characteristic));
    }
  };

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

  private void failAllSubscribers(SubscriptionCallsGroup subscription, String format, Object... args) {
    for (RpcCall rpcCall : subscription.calls) {
      notifyCallFailed(rpcCall, format, args);
    }
    // TODO: clear before calling user code.
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
    // TODO: reset before calling user code.
    reset();
  }

  private Set<RpcCall> allSubscriptionCalls() {
    return FluentIterable.from(subscriptions.values()).transformAndConcat(callsGroup -> callsGroup.calls).toSet();
  }

  private void notifyCallFailed(RpcCall rpcCall, String format, Object... args) {
    rpcCall.controller.setFailed(String.format(format, args));
    notifyDefaultResultForCall(rpcCall);
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

  private static class RpcCall {
    private final MethodDescriptor method;
    private final BleRpcController controller;
    private final Message request;
    private final Message responsePrototype;
    private final RpcCallback<Message> done;
    private final boolean isUnsubscribeCall;
    private final UUID serviceUuid;
    private final UUID characteristicUuid;
    private final UUID descriptorUuid;

    // Create normal RpcCall.
    RpcCall(MethodDescriptor method, BleRpcController controller, Message request, Message responsePrototype,
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
      String descriptorUuid = method.getOptions().getExtension(Blerpc.characteristic).getDescriptorUuid();
      return descriptorUuid.isEmpty() ? null : UUID.fromString(descriptorUuid);
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
