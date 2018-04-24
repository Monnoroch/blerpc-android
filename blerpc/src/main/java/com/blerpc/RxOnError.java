package com.blerpc;

import io.reactivex.Emitter;
import io.reactivex.SingleEmitter;
import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.functions.Consumer;
import io.reactivex.plugins.RxJavaPlugins;
import java.util.logging.Logger;

/**
 * Tools to mute and log throwing {@link UndeliverableException}, that will be thrown on
 *     {@link Emitter#onError(Throwable)} method like UncatchableException if there is no active subscription.
 * Do {@link #loggingUncatchableExceptions(Emitter, Throwable, Logger)} for safe calling
 *     {@link Emitter#onError(Throwable)} method, mute and log UncatchableException.
 * This class should not be used extensively, only in select situations where this is absolutely needed.
 */
class RxOnError {

  private RxOnError() {}

  /**
   * Send error to RxJava Observable subscriber, mute and log occurred RxJava uncaught undeliverable exceptions.
   *
   * @param subscriber - RxJava Observable subscriber for sending an error.
   * @param throwable - error that will be sent to subscriber.
   * @param logger - for logging errors.
   */
  static void loggingUncatchableExceptions(Emitter subscriber, Throwable throwable, Logger logger) {
    loggingUncatchableExceptions(() -> subscriber.onError(throwable), logger);
  }

  /**
   * Send error to RxJava Single subscriber, mute and log occurred RxJava uncaught undeliverable exceptions.
   *
   * @param subscriber - RxJava Single subscriber for sending an error.
   * @param throwable - error that will be sent to subscriber.
   * @param logger - for logging errors.
   */
  static void loggingUncatchableExceptions(SingleEmitter subscriber, Throwable throwable, Logger logger) {
    loggingUncatchableExceptions(() -> subscriber.onError(throwable), logger);
  }

  private static void loggingUncatchableExceptions(Runnable runnable, Logger logger) {
    Consumer<? super Throwable> previousErrorHandler = RxJavaPlugins.getErrorHandler();
    RxJavaPlugins.setErrorHandler(error -> muteUndeliverableException(error, logger));
    runnable.run();
    RxJavaPlugins.setErrorHandler(previousErrorHandler);
  }

  /**
   * Don't throw undeliverable exception and throw others.
   *
   * @param error - exception that will be thrown if it isn't instance of {@link UndeliverableException}.
   * @param logger - for logging {@link UndeliverableException} message.
   */
  private static void muteUndeliverableException(Throwable error, Logger logger) {
    if (error instanceof UndeliverableException) {
      logger.info(error.getMessage());
      return;
    } else if (error instanceof RuntimeException) {
      throw (RuntimeException) error;
    }
    throw new RuntimeException(error);
  }
}
