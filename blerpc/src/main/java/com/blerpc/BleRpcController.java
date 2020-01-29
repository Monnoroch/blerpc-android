package com.blerpc;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Optional;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of the {@link RpcController}.
 */
public class BleRpcController implements RpcController {

  private AtomicBoolean canceled = new AtomicBoolean(false);
  private boolean failed = false;
  private String failMassage = null;
  private Optional<RpcCallback<Void>> cancelCallback = Optional.absent();

  @Override
  public void reset() {
    canceled.set(false);
    synchronized (this) {
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
    canceled.set(true);

    Optional<RpcCallback<Void>> callback = getCallback();
    synchronized (this) {
      cancelCallback = Optional.absent();
    }
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
    return canceled.get();
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
    checkArgument(callback != null, "Can't notify on cancel: callback is null.");

    if (canceled.get()) {
      callback.run(null);
      return;
    }

    synchronized (this) {
      cancelCallback = Optional.of(callback);
    }
  }

  private synchronized Optional<RpcCallback<Void>> getCallback() {
    return this.cancelCallback;
  }

  /**
   * A callback that is called when a subscription to BLE characteristic process finished successfully.
   * It will always be called exactly once for {@link com.blerpc.proto.MethodType#SUBSCRIBE} methods.
   */
  public void onSubscribeSuccess() {
  }
}
