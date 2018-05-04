package com.device.proto;

/**
 * <pre>
 *  A service for getting information about device battery.
 * <pre>
 */
public class RxBatteryService {

  private final com.device.proto.BatteryService service;
  private final java.util.logging.Logger logger;

  public RxBatteryService(com.device.proto.BatteryService service, java.util.logging.Logger logger) {
    this.service = service;
    this.logger = logger;
  }

  /**
   * <pre>
   *  Get current device battery level.
   * <pre>
   */
  public io.reactivex.Single<com.device.proto.GetBatteryLevelResponse> readBatteryLevel(com.device.proto.GetBatteryLevelRequest request) {
      return io.reactivex.Single.create(
          subscriber -> {
            com.blerpc.BleRpcController controller = new com.blerpc.BleRpcController();
            service.readBatteryLevel(
                controller,
                request,
                response -> {
                  if (!controller.failed()) {
                    subscriber.onSuccess(response);
                  } else {
                    com.blerpc.RxOnError.loggingUncatchableExceptions(
                        subscriber, new Exception(controller.errorText()), logger);
                  }
                });
            subscriber.setCancellable(controller::startCancel);
          });
  }

  /**
   * <pre>
   *  Subscribe for receiving updates of device battery level.
   *  Will only be sending updates when battery level is lower than the one set with SetMaxUpdatePercent.
   * <pre>
   */
  public io.reactivex.Observable<com.device.proto.GetBatteryLevelResponse> getBatteryUpdates(com.device.proto.GetBatteryLevelRequest request) {
    return getBatteryUpdates(request, new com.blerpc.BleRpcController());
  }

  /**
   * <pre>
   *  Subscribe for receiving updates of device battery level.
   *  Will only be sending updates when battery level is lower than the one set with SetMaxUpdatePercent.
   * <pre>
   */
  public io.reactivex.Observable<com.device.proto.GetBatteryLevelResponse> getBatteryUpdates(com.device.proto.GetBatteryLevelRequest request, com.blerpc.BleRpcController controller) {
      return io.reactivex.Observable.create(
          subscriber -> {
            service.getBatteryUpdates(
                controller,
                request,
                response -> {
                  if (!controller.failed()) {
                    subscriber.onNext(response);
                  } else {
                    com.blerpc.RxOnError.loggingUncatchableExceptions(
                        subscriber, new Exception(controller.errorText()), logger);
                  }
                });
            subscriber.setCancellable(controller::startCancel);
          });
  }
}
