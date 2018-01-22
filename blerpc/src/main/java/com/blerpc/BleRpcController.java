package com.blerpc;

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
     * Asks that the given callback be called when the subscription was succeed. The callback will
     *     always be called exactly once and only if {@link com.blerpc.proto.MethodType} is
     *     equals {@link com.blerpc.proto.MethodType#SUBSCRIBE}.
     */
    public void onSubscribeSuccess() {}
}
