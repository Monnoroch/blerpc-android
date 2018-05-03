package com.blerpc;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.device.proto.GetBatteryLevelRequest;
import com.device.proto.GetBatteryLevelResponse;
import com.device.proto.RxBatteryService;
import com.google.protobuf.RpcCallback;
import io.reactivex.observers.TestObserver;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link RxBatteryService}. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class BatteryServiceTest {

  private static final GetBatteryLevelRequest GET_BATTERY_LEVEL_REQUEST =
      GetBatteryLevelRequest.getDefaultInstance();
  private static final GetBatteryLevelResponse GET_BATTERY_LEVEL_RESPONSE =
      GetBatteryLevelResponse.newBuilder().setBatteryLevelPercent(10).build();
  private static final GetBatteryLevelResponse GET_BATTERY_LEVEL_RESPONSE2 =
      GetBatteryLevelResponse.newBuilder().setBatteryLevelPercent(20).build();
  private static final String ERROR_TEXT = "error_text";
  @Rule public final MockitoRule rule = MockitoJUnit.rule();

  @Mock com.device.proto.BatteryService batteryServiceProto;
  @Mock Logger logger;
  @Mock BleRpcController bleRpcController;
  ArgumentCaptor<BleRpcController> controllerCaptor =
      ArgumentCaptor.forClass(BleRpcController.class);
  ArgumentCaptor<RpcCallback<GetBatteryLevelResponse>> batteryLevelCaptor =
      ArgumentCaptor.forClass(RpcCallback.class);
  RxBatteryService batteryService;

  /** Set up. */
  @Before
  public void setUp() {
    batteryService = new RxBatteryService(batteryServiceProto, logger);
  }

  @Test
  public void readBatteryLevel_success() {
    TestObserver<GetBatteryLevelResponse> testSubscriber = readBatteryLevel();

    batteryLevelCaptor.getValue().run(GET_BATTERY_LEVEL_RESPONSE);
    assertThat(testSubscriber.values().get(0)).isEqualTo(GET_BATTERY_LEVEL_RESPONSE);
    testSubscriber.assertComplete();
  }

  @Test
  public void readBatteryLevel_fail() {
    TestObserver<GetBatteryLevelResponse> testSubscriber = readBatteryLevel();

    controllerCaptor.getValue().setFailed(ERROR_TEXT);
    batteryLevelCaptor.getValue().run(GET_BATTERY_LEVEL_RESPONSE);
    testSubscriber.assertNoValues();
    assertThat(testSubscriber.errors().get(0).getMessage()).contains(ERROR_TEXT);
  }

  @Test
  public void readBatteryLevel_fail_canceled() {
    TestObserver<GetBatteryLevelResponse> testSubscriber = readBatteryLevel();

    testSubscriber.dispose();
    controllerCaptor.getValue().setFailed(ERROR_TEXT);
    batteryLevelCaptor.getValue().run(GET_BATTERY_LEVEL_RESPONSE);

    testSubscriber.assertNoValues();
    testSubscriber.assertNoErrors();
    verify(logger).info(contains(ERROR_TEXT));
  }

  @Test
  public void readBatteryLevel_cancel() {
    TestObserver<GetBatteryLevelResponse> testSubscriber = readBatteryLevel();

    testSubscriber.dispose();
    assertThat(controllerCaptor.getValue().isCanceled()).isTrue();
  }

  private TestObserver<GetBatteryLevelResponse> readBatteryLevel() {
    TestObserver<GetBatteryLevelResponse> testSubscriber =
        batteryService.readBatteryLevel(GET_BATTERY_LEVEL_REQUEST).test();
    verify(batteryServiceProto)
        .readBatteryLevel(
            controllerCaptor.capture(),
            eq(GET_BATTERY_LEVEL_REQUEST),
            batteryLevelCaptor.capture());
    return testSubscriber;
  }

  @Test
  public void getBatteryUpdates_customController() {
    TestObserver<GetBatteryLevelResponse> testSubscriber =
        batteryService.getBatteryUpdates(GET_BATTERY_LEVEL_REQUEST, bleRpcController).test();
    verify(batteryServiceProto)
        .getBatteryUpdates(
            eq(bleRpcController), eq(GET_BATTERY_LEVEL_REQUEST), batteryLevelCaptor.capture());
  }

  @Test
  public void getBatteryUpdates_success() {
    TestObserver<GetBatteryLevelResponse> testSubscriber = getBatteryUpdates();

    batteryLevelCaptor.getValue().run(GET_BATTERY_LEVEL_RESPONSE);
    assertThat(testSubscriber.values().get(0)).isEqualTo(GET_BATTERY_LEVEL_RESPONSE);

    batteryLevelCaptor.getValue().run(GET_BATTERY_LEVEL_RESPONSE2);
    assertThat(testSubscriber.values().get(1)).isEqualTo(GET_BATTERY_LEVEL_RESPONSE2);
  }

  @Test
  public void getBatteryUpdates_fail() {
    TestObserver<GetBatteryLevelResponse> testSubscriber = getBatteryUpdates();

    controllerCaptor.getValue().setFailed(ERROR_TEXT);
    batteryLevelCaptor.getValue().run(GET_BATTERY_LEVEL_RESPONSE);
    testSubscriber.assertNoValues();
    assertThat(testSubscriber.errors().get(0).getMessage()).contains(ERROR_TEXT);
  }

  @Test
  public void getBatteryUpdates_fail_canceled() {
    TestObserver<GetBatteryLevelResponse> testSubscriber = getBatteryUpdates();

    testSubscriber.dispose();
    controllerCaptor.getValue().setFailed(ERROR_TEXT);
    batteryLevelCaptor.getValue().run(GET_BATTERY_LEVEL_RESPONSE);

    testSubscriber.assertNoValues();
    testSubscriber.assertNoErrors();
    verify(logger).info(contains(ERROR_TEXT));
  }

  @Test
  public void getBatteryUpdates_cancel() {
    TestObserver<GetBatteryLevelResponse> testSubscriber = getBatteryUpdates();

    testSubscriber.dispose();
    assertThat(controllerCaptor.getValue().isCanceled()).isTrue();
  }

  private TestObserver<GetBatteryLevelResponse> getBatteryUpdates() {
    TestObserver<GetBatteryLevelResponse> testSubscriber =
        batteryService.getBatteryUpdates(GET_BATTERY_LEVEL_REQUEST).test();
    verify(batteryServiceProto)
        .getBatteryUpdates(
            controllerCaptor.capture(),
            eq(GET_BATTERY_LEVEL_REQUEST),
            batteryLevelCaptor.capture());
    return testSubscriber;
  }
}
