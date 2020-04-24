package com.blerpc;

import static com.google.common.base.Preconditions.checkArgument;

import com.blerpc.proto.Blerpc;
import com.blerpc.proto.ByteOrder;
import com.blerpc.proto.FieldExtension;
import com.blerpc.proto.MessageExtension;
import com.google.common.base.Optional;
import com.google.common.math.LongMath;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;
import java.util.List;
import java.util.Map;

/**
 * Message converter that serialize/deserialize proto message to byte array based on byte range descriptions in annotations.
 */
public class AnnotationMessageConverter implements MessageConverter {

  private final ByteOrder defaultByteOrder;

  /**
   * Create {@link AnnotationMessageConverter} instance for big endian byte order.
   */
  public AnnotationMessageConverter() {
    this(java.nio.ByteOrder.BIG_ENDIAN);
  }

  /**
   * Create {@link AnnotationMessageConverter} instance.
   *
   * @param defaultByteOrder - byte order that will be used while serialize/deserialize field values to bytes.
   */
  public AnnotationMessageConverter(java.nio.ByteOrder defaultByteOrder) {
    this.defaultByteOrder = defaultByteOrder.equals(java.nio.ByteOrder.BIG_ENDIAN)
        ? ByteOrder.BIG_ENDIAN
        : ByteOrder.LITTLE_ENDIAN;
  }

  @Override
  public byte[] serializeRequest(MethodDescriptor methodDescriptor, Message message) {
    checkHasExtension(message);
    int messageBytesSize = getMessageExtension(message).getSizeBytes();
    if (messageBytesSize == 0) {
      return new byte[0];
    }
    byte[] requestBytes = new byte[messageBytesSize];
    serializeMessage(requestBytes, message, FieldExtension.newBuilder()
        .setFromByte(0)
        .setToByte(messageBytesSize)
        .setByteOrder(defaultByteOrder)
        .build(), false);
    return requestBytes;
  }

  private void serializeMessage(byte[] requestBytes,
                                Message message,
                                FieldExtension messageFieldExtension,
                                boolean useFieldByteOrder) {
    validateMessageSchema(message, messageFieldExtension);
    for (Map.Entry<FieldDescriptor, Object> entry : message.getAllFields().entrySet()) {
      FieldDescriptor fieldDescriptor = entry.getKey();
      Object fieldValue = entry.getValue();
      String fieldName = fieldDescriptor.getName();
      @SuppressWarnings("OptionalGetWithoutIsPresent") // absent value is impossible here
      FieldExtension relativeBytesRangeFieldExtension =
          getRelativeBytesRangeFieldExtension(
                  messageFieldExtension,
                  Optional.absent(),
                  fieldDescriptor,
                  message,
                  useFieldByteOrder)
              .get();

      JavaType fieldType = fieldDescriptor.getType().getJavaType();
      switch (fieldType) {
        case MESSAGE:
          serializeMessage(requestBytes, (Message) fieldValue, relativeBytesRangeFieldExtension, hasByteOrder(fieldDescriptor));
          break;
        case INT:
          serializeInt(requestBytes, (Integer) fieldValue, relativeBytesRangeFieldExtension, fieldName);
          break;
        case LONG:
          serializeLong(requestBytes, (Long) fieldValue, relativeBytesRangeFieldExtension, fieldName);
          break;
        case ENUM:
          serializeEnum(requestBytes, (EnumValueDescriptor) fieldValue, relativeBytesRangeFieldExtension, fieldName);
          break;
        case BOOLEAN:
          serializeBoolean(requestBytes, (Boolean) fieldValue, relativeBytesRangeFieldExtension, fieldName);
          break;
        case BYTE_STRING:
          serializeByteString(requestBytes, (ByteString) fieldValue, relativeBytesRangeFieldExtension, fieldName);
          break;
        // TODO(#5): Add support of String, Float and Double.
        default:
          throw new IllegalArgumentException(String.format("Unsupported field type: %s, field name: %s",
              fieldType.name(),
              fieldName));
      }
    }
  }

  private void serializeInt(byte[] messageBytes, int fieldValue, FieldExtension fieldExtension, String fieldName) {
    int bytesSize = fieldExtension.getToByte() - fieldExtension.getFromByte();
    checkArgument(bytesSize <= 4,
        "Int32 field %s has unsupported size %s. Only sizes in [1, 4] are supported.",
        fieldName,
        bytesSize);
    serializeLong(messageBytes, fieldValue, fieldExtension, fieldName);
  }

  private void serializeLong(byte[] messageBytes, long fieldValue, FieldExtension fieldExtension, String fieldName) {
    int firstByte = fieldExtension.getFromByte();
    int bytesCount = fieldExtension.getToByte() - firstByte;
    checkArgument(bytesCount <= 8,
        "Int64 field %s has unsupported size %s. Only sizes in [1, 8] are supported.",
        fieldName,
        bytesCount);
    if (fieldExtension.getByteOrder().equals(ByteOrder.BIG_ENDIAN)) {
      for (int i = 0; i < bytesCount; i++) {
        messageBytes[firstByte + i] = (byte) (fieldValue >> (8 * (bytesCount - i - 1)));
      }
    } else {
      for (int i = 0; i < bytesCount; i++) {
        messageBytes[firstByte + i] = (byte) (fieldValue >> (8 * i));
      }
    }
  }

  private void serializeBoolean(byte[] messageBytes, boolean fieldValue, FieldExtension fieldExtension, String fieldName) {
    int bytesSize = fieldExtension.getToByte() - fieldExtension.getFromByte();
    checkArgument(bytesSize == 1,
        "Boolean field %s has unsupported size %s. Only sizes 1 are supported.",
        fieldName,
        bytesSize);
    messageBytes[fieldExtension.getFromByte()] = fieldValue ? (byte) 1 : (byte) 0;
  }

  private void serializeByteString(byte[] messageBytes, ByteString byteString, FieldExtension fieldExtension, String fieldName) {
    // TODO(#5): support for variable-length byte strings.
    int bytesSize = fieldExtension.getToByte() - fieldExtension.getFromByte();
    checkArgument(bytesSize == byteString.size(),
        "Declared size %s of ByteString %s is not equal to ByteString real size %s",
        bytesSize,
        fieldName,
        byteString.size());
    byteString.copyTo(messageBytes, fieldExtension.getFromByte());
  }

  private void serializeEnum(byte[] messageBytes, EnumValueDescriptor enumDescriptor, FieldExtension fieldExtension, String fieldName) {
    checkEnumBytesRangeValid(enumDescriptor.getType(), fieldExtension, fieldName);
    checkBytesRangeEnoughForEnum(enumDescriptor, fieldExtension);
    serializeLong(messageBytes, enumDescriptor.getNumber(), fieldExtension, fieldName);
  }

  @Override
  public Message deserializeResponse(MethodDescriptor methodDescriptor, Message message, byte[] value) {
    if (value.length == 0) {
      return message.getDefaultInstanceForType();
    }
    checkHasExtension(message);
    int messageBytesSize = getMessageExtension(message).getSizeBytes();
    return deserializeMessage(message, value, FieldExtension.newBuilder()
        .setFromByte(0)
        .setToByte(messageBytesSize)
        .setByteOrder(defaultByteOrder)
        .build(), false);
  }

  private Message deserializeMessage(Message message,
                                     byte[] value,
                                     FieldExtension messageFieldExtension,
                                     boolean useFieldByteOrder) {
    validateMessageSchema(message, messageFieldExtension);
    Message.Builder messageBuilder = message.toBuilder();
    for (FieldDescriptor fieldDescriptor : message.getDescriptorForType().getFields()) {
      Optional<FieldExtension> relativeBytesRangeFieldExtensionOptional =
          getRelativeBytesRangeFieldExtension(
              messageFieldExtension,
              Optional.of(value.length),
              fieldDescriptor,
              message,
              useFieldByteOrder);
      if (!relativeBytesRangeFieldExtensionOptional.isPresent()) {
        continue;
      }

      FieldExtension relativeBytesRangeFieldExtension =
          relativeBytesRangeFieldExtensionOptional.get();
      String fieldName = fieldDescriptor.getName();
      JavaType fieldType = fieldDescriptor.getType().getJavaType();
      switch (fieldType) {
        case MESSAGE:
          messageBuilder.setField(fieldDescriptor, deserializeMessage((Message) message.getField(fieldDescriptor),
              value,
              relativeBytesRangeFieldExtension,
              hasByteOrder(fieldDescriptor)));
          break;
        case INT:
          messageBuilder.setField(fieldDescriptor, deserializeInt(value, relativeBytesRangeFieldExtension, fieldName));
          break;
        case LONG:
          messageBuilder.setField(fieldDescriptor, deserializeLong(value, relativeBytesRangeFieldExtension, fieldName));
          break;
        case ENUM:
          messageBuilder.setField(fieldDescriptor, deserializeEnum(value, fieldDescriptor, relativeBytesRangeFieldExtension));
          break;
        case BOOLEAN:
          messageBuilder.setField(fieldDescriptor, deserializeBoolean(value, relativeBytesRangeFieldExtension, fieldName));
          break;
        case BYTE_STRING:
          messageBuilder.setField(fieldDescriptor, deserializeByteString(value, relativeBytesRangeFieldExtension));
          break;
        // TODO(#5): Add support of String, Float and Double.
        default:
          throw new IllegalArgumentException(String.format("Unsupported field type: %s, field name: %s",
              fieldType.name(),
              fieldName));
      }
    }
    return messageBuilder.build();
  }

  private int deserializeInt(byte[] bytes, FieldExtension fieldExtension, String fieldName) {
    int bytesSize = fieldExtension.getToByte() - fieldExtension.getFromByte();
    checkArgument(bytesSize <= 4,
        "Int32 field %s has unsupported size %s. Only sizes in [1, 4] are supported.",
        fieldName,
        bytesSize);
    return (int) deserializeLong(bytes, fieldExtension, fieldName);
  }

  private long deserializeLong(byte[] bytes, FieldExtension fieldExtension, String fieldName) {
    int firstByte = fieldExtension.getFromByte();
    int lastByte = fieldExtension.getToByte();
    int bytesSize = lastByte - firstByte;
    checkArgument(bytesSize <= 8,
        "Int64 field %s has unsupported size %s. Only sizes in [1, 8] are supported.",
        fieldName,
        bytesSize);

    long result = 0;
    if (fieldExtension.getByteOrder().equals(ByteOrder.BIG_ENDIAN)) {
      for (int i = firstByte; i < lastByte; i++) {
        result <<= 8;
        result |= bytes[i] & 0xFF;
      }
    } else {
      for (int i = lastByte; i > firstByte; i--) {
        result <<= 8;
        result |= bytes[i - 1] & 0xFF;
      }
    }
    return result;
  }

  private EnumValueDescriptor deserializeEnum(byte[] bytes, FieldDescriptor fieldDescriptor, FieldExtension fieldExtension) {
    checkEnumBytesRangeValid(fieldDescriptor.getEnumType(), fieldExtension, fieldDescriptor.getName());
    return fieldDescriptor.getEnumType()
        .findValueByNumber((int) deserializeLong(bytes, fieldExtension, fieldDescriptor.getName()));
  }

  private boolean deserializeBoolean(byte[] bytes, FieldExtension fieldExtension, String fieldName) {
    int bytesSize = fieldExtension.getToByte() - fieldExtension.getFromByte();
    checkArgument(bytesSize == 1,
        "Boolean field %s has unsupported size %s. Only sizes 1 are supported.",
        fieldName,
        bytesSize);
    return bytes[fieldExtension.getFromByte()] != 0;
  }

  private ByteString deserializeByteString(byte[] bytes, FieldExtension fieldExtension) {
    // TODO(#5): support for variable-length byte strings.
    int byteSize = fieldExtension.getToByte() - fieldExtension.getFromByte();
    return ByteString.copyFrom(bytes, fieldExtension.getFromByte(), byteSize);
  }

  private static MessageExtension getMessageExtension(Message message) {
    return message.getDescriptorForType().getOptions().getExtension(Blerpc.message);
  }

  private static FieldExtension getFieldExtension(FieldDescriptor descriptor) {
    return descriptor.getOptions().getExtension(Blerpc.field);
  }

  private static void validateMessageSchema(Message message, FieldExtension fieldExtension) {
    checkHasExpectedBytesSize(message, fieldExtension);
    List<FieldDescriptor> fields = message.getDescriptorForType().getFields();
    for (FieldDescriptor field : fields) {
      checkFieldHasExtension(field);
      checkBytesRangeValid(getFieldExtension(field), getMessageExtension(message).getSizeBytes(), field);
    }
    checkBytesRangesNotIntersect(fields);
  }

  private static void checkHasExtension(Message message) {
    Descriptor descriptor = message.getDescriptorForType();
    checkArgument(descriptor.getOptions().hasExtension(Blerpc.message) || descriptor.getFields().isEmpty(),
        "A non empty message %s doesn't have com.blerpc.message_extension annotation.",
        descriptor.getName());
  }

  private static void checkHasExpectedBytesSize(Message message, FieldExtension fieldExtension) {
    int messageBytesSize = getMessageExtension(message).getSizeBytes();
    int expectedBytesSize = fieldExtension.getToByte() - fieldExtension.getFromByte();
    checkArgument(messageBytesSize == expectedBytesSize,
        "Non-primitive message %s has declared size %s, which is not equal to the size of it's type %s.",
        message.getDescriptorForType().getName(),
        expectedBytesSize,
        messageBytesSize);
  }

  private static void checkFieldHasExtension(FieldDescriptor descriptor) {
    checkArgument(descriptor.getOptions().hasExtension(Blerpc.field),
        "Proto field %s doesn't have com.blerpc.field_extension annotation",
        descriptor.getName());
  }

  private static void checkBytesRangeValid(FieldExtension fieldExtension, int messageBytesSize, FieldDescriptor descriptor) {
    String name = descriptor.getName();
    int firstByte = fieldExtension.getFromByte();
    int lastByte = fieldExtension.getToByte();
    checkArgument(firstByte < lastByte,
        "Field %s has from_bytes = %s which must be less than to_bytes = %s",
        name,
        firstByte,
        lastByte);
    checkArgument(firstByte >= 0,
        "Field %s has from_bytes = %s which is less than zero",
        name,
        firstByte);
    checkArgument(lastByte <= messageBytesSize,
        "Field %s has to_bytes = %s which is bigger than message bytes size = %s",
        name,
        lastByte,
        messageBytesSize);
  }

  private static void checkBytesRangesNotIntersect(List<FieldDescriptor> fields) {
    for (int i = 0; i < fields.size(); i++) {
      for (int j = i + 1; j < fields.size(); j++) {
        FieldExtension firstExtension = getFieldExtension(fields.get(i));
        FieldExtension secondExtension = getFieldExtension(fields.get(j));
        checkArgument(!bytesRangesIntersect(firstExtension, secondExtension),
            "Field %s bytes range [%s, %s] intersects with another field %s bytes range [%s, %s]",
            fields.get(i).getName(),
            firstExtension.getFromByte(),
            firstExtension.getToByte(),
            fields.get(j).getName(),
            secondExtension.getFromByte(),
            secondExtension.getToByte());
      }
    }
  }

  private static boolean bytesRangesIntersect(FieldExtension firstExtension, FieldExtension secondExtension) {
    boolean firstRangeBeforeSecondRange = firstExtension.getFromByte() < secondExtension.getFromByte();
    return firstRangeBeforeSecondRange
        ? firstExtension.getToByte() > secondExtension.getFromByte()
        : secondExtension.getToByte() > firstExtension.getFromByte();
  }

  private static void checkBytesRangeEnoughForEnum(EnumValueDescriptor enumValueDescriptor, FieldExtension fieldExtension) {
    int bytesSize = fieldExtension.getToByte() - fieldExtension.getFromByte();
    EnumDescriptor enumDescriptor = enumValueDescriptor.getType();
    int maxValueNumber = 0;
    for (EnumValueDescriptor value : enumValueDescriptor.getType().getValues()) {
      maxValueNumber = Math.max(maxValueNumber, value.getNumber());
    }
    checkArgument(LongMath.pow(2, 8 * bytesSize) - 1 >= maxValueNumber,
        "%s byte(s) not enough for %s enum that has %s max number",
        bytesSize,
        enumDescriptor.getName(),
        maxValueNumber);
  }

  private static void checkEnumBytesRangeValid(EnumDescriptor enumDescriptor, FieldExtension fieldExtension, String fieldName) {
    int bytesSize = fieldExtension.getToByte() - fieldExtension.getFromByte();
    checkArgument(bytesSize <= 4,
        "Enum %s field %s has unsupported size %s. Only sizes in [1, 4] are supported.",
        enumDescriptor.getName(),
        fieldName,
        bytesSize);
  }

  private Optional<FieldExtension> getRelativeBytesRangeFieldExtension(FieldExtension messageFieldExtension,
                                                                       Optional<Integer> valueSizeToCheck,
                                                                       FieldDescriptor fieldDescriptor,
                                                                       Message message,
                                                                       boolean useFieldByteOrder) {
    int firstByte = messageFieldExtension.getFromByte();
    FieldExtension embeddedFieldExtension = getFieldExtension(fieldDescriptor);
    int lastByte = embeddedFieldExtension.getToByte() + firstByte;
    if (valueSizeToCheck.isPresent() && lastByte > valueSizeToCheck.get()) {
      return Optional.absent();
    }

    ByteOrder messageFieldOrder = messageFieldExtension.getByteOrder();
    return Optional.of(FieldExtension.newBuilder()
        .setFromByte(embeddedFieldExtension.getFromByte() + firstByte)
        .setToByte(lastByte)
        .setByteOrder(useFieldByteOrder ? messageFieldOrder :
            getByteOrderOrDefault(embeddedFieldExtension.getByteOrder(),
                getByteOrderOrDefault(getMessageExtension(message).getByteOrder(), messageFieldOrder)))
        .build());
  }

  private static ByteOrder getByteOrderOrDefault(ByteOrder currentByteOrder, ByteOrder defaultByteOrder) {
    return currentByteOrder.equals(ByteOrder.DEFAULT) ? defaultByteOrder : currentByteOrder;
  }

  private static boolean hasByteOrder(FieldDescriptor fieldDescriptor) {
    return !getFieldExtension(fieldDescriptor).getByteOrder().equals(ByteOrder.DEFAULT);
  }
}
