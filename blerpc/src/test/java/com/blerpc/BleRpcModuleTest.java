package com.blerpc;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.os.Handler;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import java.util.logging.Logger;
import javax.inject.Singleton;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link BleRpcModule}.
 */
@RunWith(MockitoJUnitRunner.class)
public class BleRpcModuleTest {

  private TestAppComponent testAppComponent;

  /**
   * Set up.
   */
  @Before
  public void setUp() {
    testAppComponent = DaggerBleRpcModuleTest_TestAppComponent.create();
  }

  /**
   * Tear down.
   */
  @After
  public void tearDown() {
    ServiceStubFactory.clearInstance();
  }

  @Singleton
  @Component(modules = {TestModule.class, BleRpcModule.class})
  interface TestAppComponent {
    ServiceStubFactory getServiceStubFactory();
  }

  @Module
  static class TestModule {
    @Provides
    @BleRpcModule.BleRpc
    public Context provideContext() {
      return mock(Context.class);
    }

    @Provides
    @BleRpcModule.BleRpc
    public MessageConverter provideMessageConverter() {
      return mock(MessageConverter.class);
    }

    @Provides
    @BleRpcModule.WorkHandler
    public Handler provideWorkHandler() {
      return mock(Handler.class);
    }

    @Provides
    @BleRpcModule.ListenerHandler
    public Handler provideListenerHandler() {
      return mock(Handler.class);
    }

    @Provides
    @BleRpcModule.BleRpc
    public Logger provideLogger() {
      return Logger.getGlobal();
    }
  }

  @Test
  public void provideServiceStubFactorySingleton() {
    ServiceStubFactory serviceStubFactory = testAppComponent.getServiceStubFactory();
    assertThat(serviceStubFactory).isNotNull();
    assertThat(testAppComponent.getServiceStubFactory()).isEqualTo(serviceStubFactory);
  }
}
