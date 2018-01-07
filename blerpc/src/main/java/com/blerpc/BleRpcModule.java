package com.blerpc;

import android.content.Context;
import android.os.Handler;
import dagger.Module;
import dagger.Provides;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.logging.Logger;
import javax.inject.Qualifier;
import javax.inject.Singleton;

@SuppressWarnings("SingleLineJavadoc")
/** A {@link Module} for providing the BleRpc factory used for creating rpc service stubs for bluetooth devices. */
@Module
public class BleRpcModule {

    /**
     * Provide service stub factory.
     */
    @Provides
    @Singleton
    ServiceStubFactory provideServiceStubFactory(
            @BleRpc Context context,
            @BleRpc MessageConverter messageConverter,
            @WorkHandler Handler workHandler,
            @ListenerHandler Handler listenerHandler,
            @BleRpc Logger logger) {
        return ServiceStubFactory.getInstance(context, messageConverter, workHandler, listenerHandler, logger);
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ListenerHandler {}

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    public @interface WorkHandler {}

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    public @interface BleRpc {}
}
