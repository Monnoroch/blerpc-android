package com.blerpc;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import com.blerpc.reactive.BleServiceFactory;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link BleServiceFactory}. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class BleServiceFactoryTest {

  private static final String DEVICE_ADDRESS = "address";
  @Rule public final MockitoRule rule = MockitoJUnit.rule();

  @Mock ServiceStubFactory serviceStubFactory;
  @Mock Logger logger;
  @Mock BluetoothDevice bluetoothDevice;
  private BleServiceFactory bleServiceFactory;

  /** Set up. */
  @Before
  public void setUp() {
    when(bluetoothDevice.getAddress()).thenReturn(DEVICE_ADDRESS);
    bleServiceFactory = new BleServiceFactory(serviceStubFactory, logger);
  }

  @Test
  public void createBatteryService() {
    assertThat(bleServiceFactory.createBatteryService(bluetoothDevice)).isNotNull();
  }
}
