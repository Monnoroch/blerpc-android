package com.blerpc;

import com.google.common.base.Optional;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;

/**
 * Implementation of the {@link RpcController}.
 */
public class BleRpcController implements RpcController {

  private boolean canceled;
  private boolean failed = false;
  private String failMassage = null;
  private Optional<RpcCallback<Void>> cancelCallback = Optional.absent();

  @Override
  public void reset() {
    synchronized (this) {
      canceled = false;
      failed = false;
      failMassage = null;
      cancelCallback = Optional.absent();
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
    synchronized (this) {
      canceled = true;
    }

    Optional<RpcCallback<Void>> callback = getAndClearCallback();
    if (callback.isPresent()) {
      callback.get().run(null);
    }
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
    synchronized (this) {
      return canceled;
    }
  }

  @Override
  public void notifyOnCancel(RpcCallback<Object> callback) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  /**
   * Assings a custom callback, which will be called once for:
   * <ul>
   *   <li> cancel event;
   *   <li> dispose event.
   * </ul>
   *
   * @param callback callback for notifying about cancel events
   */
  void runOnCancel(RpcCallback<Void> callback) {
    if (isCanceled()) {
      callback.run(null);
      return;
    }

    synchronized (this) {
      cancelCallback = Optional.of(callback);
    }
  }

  private synchronized Optional<RpcCallback<Void>> getAndClearCallback() {
    synchronized (this) {
      if (!cancelCallback.isPresent()) {
        return Optional.absent();
      }
      Optional<RpcCallback<Void>> callback = cancelCallback;
      cancelCallback = Optional.absent();
      return callback;
    }
  }

  /**
   * A callback that is called when a subscription to BLE characteristic process finished successfully.
   * It will always be called exactly once for {@link com.blerpc.proto.MethodType#SUBSCRIBE} methods.
   */
  public void onSubscribeSuccess() {
  }
}
