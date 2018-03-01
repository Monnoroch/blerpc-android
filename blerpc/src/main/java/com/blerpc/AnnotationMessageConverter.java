package com.blerpc;

import static com.google.common.base.Preconditions.checkArgument;

import com.blerpc.proto.Blerpc;
import com.blerpc.proto.BytesRange;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;
import java.nio.ByteOrder;
import java.util.Map;

/**
 * Message converter that serialize/deserialize proto message to byte array based on byte range descriptions in annotations.
 */
public class AnnotationMessageConverter implements MessageConverter {

    private final ByteOrder byteOrder;

    /**
     * Create {@link AnnotationMessageConverter} instance for big endian byte order.
     */
    public AnnotationMessageConverter() {
        this(ByteOrder.BIG_ENDIAN);
    }

    /**
     * Create {@link AnnotationMessageConverter} instance.
     *
     * @param byteOrder - byte order that will be used while serialize/deserialize field values to bytes.
     */
    public AnnotationMessageConverter(ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
    }

    @Override
    public byte[] serializeRequest(MethodDescriptor methodDescriptor, Message message) throws CouldNotConvertMessageException {
        int messageBytesSize = getMessageBytesSize(message);
        if (messageBytesSize == 0) {
            return new byte[0];
        }
        byte[] requestBytes = new byte[messageBytesSize];
        serializeMessage(requestBytes, message, BytesRange.newBuilder()
                .setFromByte(0)
                .setToByte(messageBytesSize)
                .build());
        return requestBytes;
    }

    private void serializeMessage(byte[] requestBytes, Message message, BytesRange requestBytesRange) throws CouldNotConvertMessageException {
        checkHasExpectedBytesSize(message, requestBytesRange);
        int firstByte = requestBytesRange.getFromByte();
        for (Map.Entry<FieldDescriptor, Object> entry : message.getAllFields().entrySet()) {
            FieldDescriptor fieldDescriptor = entry.getKey();
            Object fieldValue = entry.getValue();
            String fieldName = fieldDescriptor.getName();
            BytesRange fieldBytesRange = getBytesRange(
                    requestBytesRange.getToByte() - requestBytesRange.getFromByte(),
                    fieldDescriptor);
            BytesRange relativeFieldBytesRange = fieldBytesRange.toBuilder()
                    .setFromByte(fieldBytesRange.getFromByte() + firstByte)
                    .setToByte(fieldBytesRange.getToByte() + firstByte)
                    .build();
            checkBytesRangeNotFilled(requestBytes, relativeFieldBytesRange, fieldName);
            JavaType fieldType = fieldDescriptor.getType().getJavaType();
            switch (fieldType) {
                case MESSAGE:
                    serializeMessage(requestBytes, (Message) fieldValue, relativeFieldBytesRange);
                    break;
                case INT:
                    serializeLong(requestBytes, (Integer) fieldValue, relativeFieldBytesRange, fieldName);
                    break;
                case LONG:
                    serializeLong(requestBytes, (Long) fieldValue, relativeFieldBytesRange, fieldName);
                    break;
                case BYTE_STRING:
                    serializeByteString(requestBytes, (ByteString) fieldValue, relativeFieldBytesRange, fieldName);
                    break;
                case ENUM:
                    serializeEnum(requestBytes, (EnumValueDescriptor) fieldValue, relativeFieldBytesRange, fieldName);
                    break;
                case BOOLEAN:
                    serializeBoolean(requestBytes, (Boolean) fieldValue, relativeFieldBytesRange);
                    break;
                // TODO(#5): Add support of String, Float and Double.
                default:
                    checkArgument(false, "Unsupported field type: %s, field name: %s", fieldType.name(), fieldName);
            }
        }
    }

    private void serializeLong(byte[] messageBytes, long fieldValue, BytesRange bytesRange, String fieldName) {
        int firstByte = bytesRange.getFromByte();
        int bytesCount = bytesRange.getToByte() - firstByte;
        checkArgument(bytesCount <= 8,
                "Only integer fields with declared bytes size in [1, 8] are supported. Field %s has %s bytes size.",
                fieldName,
                bytesCount);
        if (byteOrder.equals(ByteOrder.BIG_ENDIAN)) {
            for (int i = 0; i < bytesCount; i++) {
                messageBytes[firstByte + i] = (byte) (fieldValue >>> 8 * (bytesCount - i - 1));
            }
        } else {
            for (int i = 0; i < bytesCount; i++) {
                messageBytes[firstByte + i] = (byte) (fieldValue >>> 8 * i);
            }
        }
    }

    private void serializeBoolean(byte[] messageBytes, boolean fieldValue, BytesRange bytesRange) {
        messageBytes[bytesRange.getFromByte()] = fieldValue ? (byte) 1 : (byte) 0;
    }

    private void serializeEnum(byte[] messageBytes, EnumValueDescriptor enumDescriptor, BytesRange bytesRange, String fieldName) {
        checkBytesRangeEnoughForEnum(enumDescriptor, bytesRange, fieldName);
        serializeLong(messageBytes, enumDescriptor.getNumber(), bytesRange, fieldName);
    }

    private void serializeByteString(byte[] messageBytes, ByteString fieldValue, BytesRange bytesRange, String fieldName)
            throws CouldNotConvertMessageException {
        int expectedBytesSize = bytesRange.getToByte() - bytesRange.getFromByte();
        if (fieldValue.size() != expectedBytesSize) {
            throw CouldNotConvertMessageException.serializeRequest(
                    "Actual byte string size %s is not equal to the declared field size %s, field name: %s",
                    fieldValue.size(),
                    expectedBytesSize,
                    fieldName);
        }
        fieldValue.copyTo(messageBytes, bytesRange.getFromByte());
    }

    @Override
    public Message deserializeResponse(MethodDescriptor methodDescriptor, Message message, byte[] value) {
        if (value.length == 0) {
            return message.getDefaultInstanceForType();
        }
        int messageBytesSize = getMessageBytesSize(message);
        checkArgument(value.length == messageBytesSize,
                "Message %s byte size %s is not equals to expected size of device response %s",
                message.getDescriptorForType().getName(),
                value.length,
                messageBytesSize);
        return deserializeMessage(message, value, BytesRange.newBuilder()
                .setFromByte(0)
                .setToByte(messageBytesSize)
                .build());
    }

    private Message deserializeMessage(Message message, byte[] value, BytesRange requestBytesRange) {
        checkHasExpectedBytesSize(message, requestBytesRange);
        int firstByte = requestBytesRange.getFromByte();
        Message.Builder messageBuilder = message.toBuilder();
        for (FieldDescriptor fieldDescriptor : message.getDescriptorForType().getFields()) {
            BytesRange fieldBytesRange = getBytesRange(
                    requestBytesRange.getToByte() - requestBytesRange.getFromByte(),
                    fieldDescriptor);
            BytesRange relativeFieldBytesRange = fieldBytesRange.toBuilder()
                    .setFromByte(fieldBytesRange.getFromByte() + firstByte)
                    .setToByte(fieldBytesRange.getToByte() + firstByte)
                    .build();
            String fieldName = fieldDescriptor.getName();
            JavaType fieldType = fieldDescriptor.getType().getJavaType();
            switch (fieldType) {
                case MESSAGE:
                    messageBuilder.setField(fieldDescriptor, deserializeMessage((Message) message.getField(fieldDescriptor),
                            value,
                            relativeFieldBytesRange));
                    break;
                case INT:
                    messageBuilder.setField(fieldDescriptor, (int) deserializeLong(value, relativeFieldBytesRange, fieldName));
                    break;
                case LONG:
                    messageBuilder.setField(fieldDescriptor, deserializeLong(value, relativeFieldBytesRange, fieldName));
                    break;
                case BYTE_STRING:
                    messageBuilder.setField(fieldDescriptor, deserializeByteString(value, relativeFieldBytesRange));
                    break;
                case ENUM:
                    messageBuilder.setField(fieldDescriptor, fieldDescriptor.getEnumType()
                            .findValueByNumber((int) deserializeLong(value, relativeFieldBytesRange, fieldName)));
                    break;
                case BOOLEAN:
                    messageBuilder.setField(fieldDescriptor, deserializeBoolean(value, relativeFieldBytesRange));
                    break;
                // TODO(#5): Add support of String, Float and Double.
                default:
                    checkArgument(false, "Unsupported field type: %s, field name: %s", fieldType.name(), fieldName);
            }
        }
        return messageBuilder.build();
    }

    private long deserializeLong(byte[] bytes, BytesRange bytesRange, String fieldName) {
        int firstByte = bytesRange.getFromByte();
        int lastByte = bytesRange.getToByte();
        int bytesSize = lastByte - firstByte;
        checkArgument(bytesSize <= 8,
                "Only integer fields with declared bytes size in [1, 8] are supported. Field %s has %s bytes size.",
                fieldName,
                bytesSize);

        long result = 0;
        if (byteOrder.equals(ByteOrder.BIG_ENDIAN)) {
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

    private boolean deserializeBoolean(byte[] bytes, BytesRange bytesRange) {
        return bytes[bytesRange.getFromByte()] != 0;
    }

    private ByteString deserializeByteString(byte[] bytes, BytesRange bytesRange) {
        return ByteString.copyFrom(bytes, bytesRange.getFromByte(), bytesRange.getToByte() - bytesRange.getFromByte());
    }

    private static int getMessageBytesSize(Message message) {
        Descriptor descriptor = message.getDescriptorForType();
        MessageOptions options = descriptor.getOptions();
        checkArgument(options.hasExtension(Blerpc.messageSize) || descriptor.getFields().isEmpty(),
                "Proto message %s with fields must have BytesSize option",
                descriptor.getName());
        return options.getExtension(Blerpc.messageSize).getMessageSizeBytes();
    }

    private static void checkHasExpectedBytesSize(Message message, BytesRange bytesRange) {
        int messageBytesSize = getMessageBytesSize(message);
        int expectedBytesSize = bytesRange.getToByte() - bytesRange.getFromByte();
        checkArgument(messageBytesSize == expectedBytesSize,
                "Field bytes size %s is not equals to message %s bytes size %s related to this field",
                expectedBytesSize,
                message.getDescriptorForType().getName(),
                messageBytesSize);
    }

    private BytesRange getBytesRange(int fieldBytesSize, FieldDescriptor descriptor) {
        checkFieldHasByteRangeOption(descriptor);
        BytesRange bytesRange = descriptor.getOptions().getExtension(Blerpc.filedBytes);
        checkBytesRangeValid(bytesRange, fieldBytesSize, descriptor);
        return bytesRange;
    }

    private static void checkFieldHasByteRangeOption(FieldDescriptor descriptor) {
        checkArgument(descriptor.getOptions().hasExtension(Blerpc.filedBytes),
                "Proto field %s doesn't have ByteRange option",
                descriptor.getName());
    }

    private static void checkBytesRangeValid(BytesRange bytesRange, int messageBytesSize, FieldDescriptor descriptor) {
        String name = descriptor.getName();
        int firstByte = bytesRange.getFromByte();
        int lastByte = bytesRange.getToByte();
        checkArgument(firstByte < lastByte,
                "Field %s has from_bytes = %s which must be less than to_bytes = %s",
                name,
                firstByte,
                lastByte);
        checkArgument(firstByte >= 0,
                "Field %s has from_bytes = %s which is less than zero",
                name,
                firstByte);
        checkArgument(bytesRange.getToByte() <= messageBytesSize,
                "Field %s has to_bytes = %s which is bigger than message bytes size = %s",
                name,
                lastByte,
                messageBytesSize);
        checkBooleanOccupyByte(bytesRange, descriptor);
    }

    private static void checkBooleanOccupyByte(BytesRange bytesRange, FieldDescriptor descriptor) {
        if (!descriptor.getType().getJavaType().equals(JavaType.BOOLEAN)) {
            return;
        }
        int bytesSize = bytesRange.getToByte() - bytesRange.getFromByte();
        checkArgument(bytesSize == 1,
                "Boolean value %s has bytes size = %s that more than 1 byte",
                descriptor.getName(),
                bytesSize);
    }

    private static void checkBytesRangeNotFilled(byte[] messageBytes, BytesRange bytesRange, String name) {
        boolean bytesRangeFilled = false;
        int lastByte = bytesRange.getToByte();
        for (int i = bytesRange.getFromByte(); i < lastByte; i++) {
            if (messageBytes[i] != (byte) 0) {
                bytesRangeFilled = true;
                break;
            }
        }
        checkArgument(!bytesRangeFilled, "Field %s bytes range intersects with another field bytes range", name);
    }

    @VisibleForTesting static void checkBytesRangeEnoughForEnum(EnumValueDescriptor enumValueDescriptor, BytesRange bytesRange, String fieldName) {
        int bytesSize = bytesRange.getToByte() - bytesRange.getFromByte();
        checkArgument(bytesSize <= 2,
                "Only enum fields with declared bytes size in [1, 2] are supported. Field %s has %s bytes size.",
                fieldName,
                bytesSize);
        EnumDescriptor enumDescriptor = enumValueDescriptor.getType();
        long valuesCount = enumDescriptor.getValues().size();
        checkArgument(pow(2, 8 * bytesSize) - 1 >= valuesCount,
                "%s byte(s) not enough for %s enum that has %s values",
                bytesSize,
                enumDescriptor.getName(),
                valuesCount);
    }

    private static long pow(long a, int b) {
        long result = 1;
        for (int i = 1; i <= b; i++) {
            result *= a;
        }
        return result;
    }
}
