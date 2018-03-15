package com.blerpc;

import com.google.protobuf.Descriptors.MethodDescriptor;

/**
 * Wrapper over the {@link MethodDescriptor} that provides {@link #hashCode()} and {@link
 * #equals(Object)} methods.
 */
class MethodDescriptorEqualsWrapper {

  private final MethodDescriptor methodDescriptor;

  /**
   * Create a {@link MethodDescriptorEqualsWrapper}.
   *
   * @param methodDescriptor - {@link MethodDescriptor} object.
   */
  public MethodDescriptorEqualsWrapper(MethodDescriptor methodDescriptor) {
    this.methodDescriptor = methodDescriptor;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MethodDescriptorEqualsWrapper)) {
      return false;
    }
    return methodDescriptor.getFullName()
        .equals(((MethodDescriptorEqualsWrapper) o).methodDescriptor.getFullName());
  }

  @Override
  public int hashCode() {
    return methodDescriptor.getFullName().hashCode();
  }
}
