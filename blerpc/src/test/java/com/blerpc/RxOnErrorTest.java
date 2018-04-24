package com.blerpc;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;

import com.google.common.base.Optional;
import io.reactivex.Emitter;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.functions.Consumer;
import io.reactivex.observers.TestObserver;
import io.reactivex.plugins.RxJavaPlugins;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests for {@link RxOnError}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class RxOnErrorTest {

    private static final Throwable THROWABLE = new Throwable("Error text");
    @Rule public final MockitoRule rule = MockitoJUnit.rule();

    @Mock Consumer<Throwable> errorHandler;
    @Mock Logger logger;
    Emitter<Optional<Void>> observableSubscriber;
    Observable<Optional<Void>> observable = Observable.create(subscriber -> observableSubscriber = subscriber);
    SingleEmitter<Optional<Void>> singleSubscriber;
    Single<Optional<Void>> single = Single.create(subscriber -> singleSubscriber = subscriber);

    @Test
    public void loggingUncatchableExceptionsTest_observableSubscriber() throws Exception {
        loggingUncatchableExceptions_observableSubscriber();
    }

    @Test
    public void loggingUncatchableExceptions_observableSubscriber_returnHandler() throws Exception {
        RxJavaPlugins.setErrorHandler(errorHandler);
        loggingUncatchableExceptions_observableSubscriber();
        assertThat(errorHandler).isEqualTo(RxJavaPlugins.getErrorHandler());
    }

    private void loggingUncatchableExceptions_observableSubscriber() throws Exception {
        TestObserver<Optional<Void>> testObserver = observable.test();
        testObserver.dispose();
        RxOnError.loggingUncatchableExceptions(observableSubscriber, THROWABLE, logger);
        verify(logger).info(contains(THROWABLE.getMessage()));
    }

    @Test
    public void loggingUncatchableExceptionsTest_singleSubscriber() throws Exception {
        loggingUncatchableExceptions_singleSubscriber();
    }

    @Test
    public void loggingUncatchableExceptions_singleSubscriber_returnHandler() throws Exception {
        RxJavaPlugins.setErrorHandler(errorHandler);
        loggingUncatchableExceptions_singleSubscriber();
        assertThat(errorHandler).isEqualTo(RxJavaPlugins.getErrorHandler());
    }

    private void loggingUncatchableExceptions_singleSubscriber() throws Exception {
        TestObserver<Optional<Void>> testObserver = single.test();
        testObserver.dispose();
        RxOnError.loggingUncatchableExceptions(singleSubscriber, THROWABLE, logger);

        verify(logger).info(contains(THROWABLE.getMessage()));
    }
}
