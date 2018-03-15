package com.blerpc;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.protobuf.Descriptors.MethodDescriptor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link MethodDescriptorEqualsWrapper}.
 */
@RunWith(MockitoJUnitRunner.class)
public class MethodDescriptorEqualsWrapperTest {

  private static final String NAME = "NAME";
  private static final String NAME_ANOTHER = "NAME_ANOTHER";
  private static final MethodDescriptorEqualsWrapper WRAPPER_NULL = null;

  @Mock private MethodDescriptor method;
  @Mock private MethodDescriptor methodAnother;

  private MethodDescriptorEqualsWrapper wrapper;
  private MethodDescriptorEqualsWrapper wrapperEq;
  private MethodDescriptorEqualsWrapper wrapperAnother;

  /**
   * Prepare methods.
   */
  @Before
  public void setUp() {
    wrapper = new MethodDescriptorEqualsWrapper(method);
    wrapperEq = new MethodDescriptorEqualsWrapper(method);
    wrapperAnother = new MethodDescriptorEqualsWrapper(methodAnother);

    when(method.getFullName()).thenReturn(NAME);
    when(methodAnother.getFullName()).thenReturn(NAME_ANOTHER);
  }

  @Test
  public void testEqual() {
    assertThat(wrapper).isEqualTo(wrapperEq);

    assertThat(wrapper).isNotEqualTo(wrapperAnother);
    assertThat(WRAPPER_NULL).isNotEqualTo(wrapper);
    assertThat(wrapper).isNotEqualTo(WRAPPER_NULL);
  }

  @Test
  public void testHashCode() {
    assertThat(wrapper.hashCode()).isEqualTo(wrapper.hashCode());
    assertThat(wrapper.hashCode()).isEqualTo(wrapperEq.hashCode());
  }
}
