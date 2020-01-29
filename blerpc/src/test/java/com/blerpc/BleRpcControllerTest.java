package com.blerpc;

import static com.blerpc.Assert.assertError;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.protobuf.RpcCallback;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link BleRpcController}.
 */
@RunWith(MockitoJUnitRunner.class)
public class BleRpcControllerTest {

  private static final String TEST_FAIL_MESSAGE = "TEST_FAIL_MESSAGE";
  private static final RpcCallback<Object> TEST_RPC_CALLBACK = parameter -> {

  };

  @Mock
  RpcCallback<Void> cancelCallback;

  private final BleRpcController bleRpcController = new BleRpcController();

  @Test
  public void testInitialState() {
    verifyInitialState();
  }

  @Test
  public void testStartCancel() {
    bleRpcController.startCancel();
    assertThat(bleRpcController.isCanceled()).isTrue();
    assertThat(bleRpcController.failed()).isFalse();
    assertThat(bleRpcController.errorText()).isNull();
    verifyZeroInteractions(cancelCallback);
  }

  @Test
  public void testStartCancel_notify() {
    bleRpcController.runOnCancel(cancelCallback);
    bleRpcController.startCancel();

    verify(cancelCallback).run(null);
  }

  @Test
  public void testStartCancel_notifyTwice_oneCall() {
    bleRpcController.runOnCancel(cancelCallback);
    bleRpcController.startCancel();
    bleRpcController.startCancel();

    verify(cancelCallback).run(null);
  }

  @Test
  public void testSetFailed() {
    bleRpcController.setFailed(TEST_FAIL_MESSAGE);
    assertThat(bleRpcController.isCanceled()).isFalse();
    assertThat(bleRpcController.failed()).isTrue();
    assertThat(bleRpcController.errorText()).isEqualTo(TEST_FAIL_MESSAGE);
  }

  @Test
  public void testStartCancel_ifFailed() {
    bleRpcController.setFailed(TEST_FAIL_MESSAGE);
    bleRpcController.startCancel();
    assertThat(bleRpcController.isCanceled()).isTrue();
    assertThat(bleRpcController.failed()).isTrue();
    assertThat(bleRpcController.errorText()).isEqualTo(TEST_FAIL_MESSAGE);
  }

  @Test
  public void testSetFailed_ifCanceled() {
    bleRpcController.startCancel();
    bleRpcController.setFailed(TEST_FAIL_MESSAGE);
    assertThat(bleRpcController.isCanceled()).isTrue();
    assertThat(bleRpcController.failed()).isTrue();
    assertThat(bleRpcController.errorText()).isEqualTo(TEST_FAIL_MESSAGE);
  }

  @Test
  public void testReset() {
    bleRpcController.setFailed(TEST_FAIL_MESSAGE);
    bleRpcController.startCancel();
    assertThat(bleRpcController.isCanceled()).isTrue();
    assertThat(bleRpcController.failed()).isTrue();
    assertThat(bleRpcController.errorText()).isEqualTo(TEST_FAIL_MESSAGE);

    bleRpcController.reset();

    verifyInitialState();
  }

  @Test
  public void testNotifyOnCancel() {
    assertError(() -> bleRpcController.notifyOnCancel(TEST_RPC_CALLBACK), "Not implemented.");
  }

    @Test
  public void testRunOnCancel() {
    bleRpcController.runOnCancel(cancelCallback);
  }

  @Test
  public void testRunOnCancel_nullCallback() {
    assertError(() -> bleRpcController.runOnCancel(null), "callback is null.");
  }

  @Test
  public void testRunOnCancel_immediatelyNotify() {
    bleRpcController.startCancel();
    bleRpcController.runOnCancel(cancelCallback);
    verify(cancelCallback).run(null);
  }

  private void verifyInitialState() {
    assertThat(bleRpcController.isCanceled()).isFalse();
    assertThat(bleRpcController.failed()).isFalse();
    assertThat(bleRpcController.errorText()).isNull();
  }
}
