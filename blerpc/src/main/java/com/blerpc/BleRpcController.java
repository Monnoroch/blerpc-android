package com.blerpc;

import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import com.google.protobuf.Service;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of the {@link RpcController}.
 */
public class BleRpcController implements RpcController {

  private final BleRpcChannel bleRpcChannel;
  private AtomicBoolean canceled = new AtomicBoolean(false);
  private boolean failed = false;
  private String failMassage = null;

  /**
   * Create {@link BleRpcController} instance.
   *
   * @param serviceStub - protobuf service stub that contains {@link BleRpcChannel}.
   */
  public BleRpcController(Service serviceStub) {
    try {
      Method getChannel = serviceStub.getClass().getMethod("getChannel");
      bleRpcChannel = (BleRpcChannel) getChannel.invoke(serviceStub);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
      throw new RuntimeException("Service stub must contain getChannel method");
    }
  }

  @Override
  public void reset() {
    canceled.set(false);
    synchronized (this) {
      failed = false;
      failMassage = null;
    }
  }

  @Override
  public boolean failed() {
    synchronized (this) {
      return failed;
    }
  }

  @Override
  public String errorText() {
    synchronized (this) {
      return failMassage;
    }
  }

  @Override
  public void startCancel() {
    canceled.set(true);
    bleRpcChannel.cancelSubscription(this);
  }

  @Override
  public void setFailed(String reason) {
    synchronized (this) {
      failMassage = reason;
      failed = true;
    }
  }

  @Override
  public boolean isCanceled() {
    return canceled.get();
  }

  @Override
  public void notifyOnCancel(RpcCallback<Object> callback) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  /**
   * A callback that is called when a subscription to BLE characteristic process finished successfully.
   * It will always be called exactly once for {@link com.blerpc.proto.MethodType#SUBSCRIBE} methods.
   */
  public void onSubscribeSuccess() {
  }
}
