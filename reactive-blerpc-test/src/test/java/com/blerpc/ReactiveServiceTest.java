package com.blerpc;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.device.proto.GetValueRequest;
import com.device.proto.GetValueResponse;
import com.device.proto.RxTestService;
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

/** Tests for rx service wrapper generated by blerpc reactive plugin. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class ReactiveServiceTest {

  private static final GetValueRequest GET_VALUE_REQUEST = GetValueRequest.getDefaultInstance();
  private static final GetValueResponse GET_VALUE_RESPONSE =
      GetValueResponse.newBuilder().setIntValue(10).build();
  private static final GetValueResponse GET_VALUE_RESPONSE2 =
      GetValueResponse.newBuilder().setIntValue(20).build();
  private static final String ERROR_TEXT = "error_text";
  @Rule public final MockitoRule rule = MockitoJUnit.rule();

  @Mock com.device.proto.TestService testServiceProto;
  @Mock Logger logger;
  @Mock BleRpcController bleRpcController;
  ArgumentCaptor<BleRpcController> controllerCaptor =
      ArgumentCaptor.forClass(BleRpcController.class);
  ArgumentCaptor<RpcCallback<GetValueResponse>> valueCaptor =
      ArgumentCaptor.forClass(RpcCallback.class);
  RxTestService testService;

  /** Set up. */
  @Before
  public void setUp() {
    testService = new RxTestService(testServiceProto, logger);
  }

  @Test
  public void readValue_success() {
    TestObserver<GetValueResponse> testSubscriber = readValue();

    valueCaptor.getValue().run(GET_VALUE_RESPONSE);
    assertThat(testSubscriber.values().get(0)).isEqualTo(GET_VALUE_RESPONSE);
    testSubscriber.assertComplete();
  }

  @Test
  public void readValue_fail() {
    TestObserver<GetValueResponse> testSubscriber = readValue();

    controllerCaptor.getValue().setFailed(ERROR_TEXT);
    valueCaptor.getValue().run(GET_VALUE_RESPONSE);
    testSubscriber.assertNoValues();
    assertThat(testSubscriber.errors().get(0).getMessage()).contains(ERROR_TEXT);
  }

  @Test
  public void readValue_fail_canceled() {
    TestObserver<GetValueResponse> testSubscriber = readValue();

    testSubscriber.dispose();
    controllerCaptor.getValue().setFailed(ERROR_TEXT);
    valueCaptor.getValue().run(GET_VALUE_RESPONSE);

    testSubscriber.assertNoValues();
    testSubscriber.assertNoErrors();
    verify(logger).warning(contains(ERROR_TEXT));
  }

  @Test
  public void readValue_cancel() {
    TestObserver<GetValueResponse> testSubscriber = readValue();

    testSubscriber.dispose();
    assertThat(controllerCaptor.getValue().isCanceled()).isTrue();
  }

  private TestObserver<GetValueResponse> readValue() {
    TestObserver<GetValueResponse> testSubscriber = testService.readValue(GET_VALUE_REQUEST).test();
    verify(testServiceProto)
        .readValue(controllerCaptor.capture(), eq(GET_VALUE_REQUEST), valueCaptor.capture());
    return testSubscriber;
  }

  @Test
  public void getValueUpdates_customController() {
    TestObserver<GetValueResponse> testSubscriber =
        testService.getValueUpdates(GET_VALUE_REQUEST, bleRpcController).test();
    verify(testServiceProto)
        .getValueUpdates(eq(bleRpcController), eq(GET_VALUE_REQUEST), valueCaptor.capture());
  }

  @Test
  public void getValueUpdates_success() {
    TestObserver<GetValueResponse> testSubscriber = getValueUpdates();

    valueCaptor.getValue().run(GET_VALUE_RESPONSE);
    assertThat(testSubscriber.values().get(0)).isEqualTo(GET_VALUE_RESPONSE);

    valueCaptor.getValue().run(GET_VALUE_RESPONSE2);
    assertThat(testSubscriber.values().get(1)).isEqualTo(GET_VALUE_RESPONSE2);
  }

  @Test
  public void getValueUpdates_fail() {
    TestObserver<GetValueResponse> testSubscriber = getValueUpdates();

    controllerCaptor.getValue().setFailed(ERROR_TEXT);
    valueCaptor.getValue().run(GET_VALUE_RESPONSE);
    testSubscriber.assertNoValues();
    assertThat(testSubscriber.errors().get(0).getMessage()).contains(ERROR_TEXT);
  }

  @Test
  public void getValueUpdates_fail_canceled() {
    TestObserver<GetValueResponse> testSubscriber = getValueUpdates();

    testSubscriber.dispose();
    controllerCaptor.getValue().setFailed(ERROR_TEXT);
    valueCaptor.getValue().run(GET_VALUE_RESPONSE);

    testSubscriber.assertNoValues();
    testSubscriber.assertNoErrors();
    verify(logger).warning(contains(ERROR_TEXT));
  }

  @Test
  public void getValueUpdates_cancel() {
    TestObserver<GetValueResponse> testSubscriber = getValueUpdates();

    testSubscriber.dispose();
    assertThat(controllerCaptor.getValue().isCanceled()).isTrue();
  }

  private TestObserver<GetValueResponse> getValueUpdates() {
    TestObserver<GetValueResponse> testSubscriber =
        testService.getValueUpdates(GET_VALUE_REQUEST).test();
    verify(testServiceProto)
        .getValueUpdates(controllerCaptor.capture(), eq(GET_VALUE_REQUEST), valueCaptor.capture());
    return testSubscriber;
  }
}