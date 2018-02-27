package com.blerpc;

import static com.google.common.base.Preconditions.checkArgument;

import com.blerpc.proto.Blerpc;
import com.blerpc.proto.BytesRange;
import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
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
        byte[] messageBytes = new byte[messageBytesSize];
        for (Map.Entry<FieldDescriptor, Object> entry : message.getAllFields().entrySet()) {
            FieldDescriptor fieldDescriptor = entry.getKey();
            Object fieldObject = entry.getValue();
            String fieldName = fieldDescriptor.getName();
            BytesRange bytesRange = getBytesRange(messageBytes, fieldDescriptor);
            int firstByte = bytesRange.getFromByte();
            int fieldBytesSize = bytesRange.getToByte() - bytesRange.getFromByte();
            JavaType fieldType = fieldDescriptor.getType().getJavaType();
            switch (fieldType) {
                case MESSAGE:
                    setBytesToArray(messageBytes, firstByte, serializeRequest(null, (Message) fieldObject));
                    break;
                case INT:
                    setBytesToArray(messageBytes, firstByte, serializeLong((Integer) fieldObject, fieldBytesSize, fieldName));
                    break;
                case LONG:
                    setBytesToArray(messageBytes, firstByte, serializeLong((Long) fieldObject, fieldBytesSize, fieldName));
                    break;
                case BYTE_STRING:
                    setBytesToArray(messageBytes, firstByte, serializeByteString((ByteString) fieldObject, fieldBytesSize));
                    break;
                case ENUM:
                    setBytesToArray(messageBytes, firstByte, serializeEnum((EnumValueDescriptor) fieldObject, fieldBytesSize, fieldName));
                    break;
                case BOOLEAN:
                    setBytesToArray(messageBytes, firstByte, serializeBoolean((Boolean) fieldObject));
                    break;
                // TODO(#5): Add support of String, Float and Double.
                default:
                    throw CouldNotConvertMessageException.serializeRequest(String.format("Unsupported field type: %s", fieldType.name()));
            }
        }
        return messageBytes;
    }

    @Override
    public Message deserializeResponse(MethodDescriptor methodDescriptor, Message message, byte[] value) throws CouldNotConvertMessageException {
        if (value.length == 0) {
            return message.getDefaultInstanceForType();
        }
        int messageBytesSize = getMessageBytesSize(message);
        checkArgument(value.length == messageBytesSize,
                String.format("Message byte size %d is not equals to expected size of device response %d", value.length, messageBytesSize));
        Message.Builder messageBuilder = message.toBuilder();
        for (FieldDescriptor fieldDescriptor : message.getDescriptorForType().getFields()) {
            BytesRange bytesRange = getBytesRange(messageBytesSize, fieldDescriptor);
            String fieldName = fieldDescriptor.getName();
            JavaType fieldType = fieldDescriptor.getType().getJavaType();
            switch (fieldType) {
                case MESSAGE:
                    messageBuilder.setField(fieldDescriptor, deserializeResponse(
                            null,
                            (Message) message.getField(fieldDescriptor),
                            copyArrayRange(value, bytesRange)));
                    break;
                case INT:
                    messageBuilder.setField(fieldDescriptor, (int) deserializeLong(copyArrayRange(value, bytesRange), fieldName));
                    break;
                case LONG:
                    messageBuilder.setField(fieldDescriptor, deserializeLong(copyArrayRange(value, bytesRange), fieldName));
                    break;
                case BYTE_STRING:
                    messageBuilder.setField(fieldDescriptor, deserializeByteString(copyArrayRange(value, bytesRange)));
                    break;
                case ENUM:
                    messageBuilder.setField(fieldDescriptor, fieldDescriptor.getEnumType()
                            .findValueByNumber((int) deserializeLong(copyArrayRange(value, bytesRange), fieldName)));
                    break;
                case BOOLEAN:
                    messageBuilder.setField(fieldDescriptor, deserializeBoolean(value[bytesRange.getFromByte()]));
                    break;
                // TODO(#5): Add support of String, Float and Double.
                default:
                    throw CouldNotConvertMessageException.serializeRequest(String.format("Unsupported field type: %s", fieldType.name()));
            }
        }
        return messageBuilder.build();
    }

    private byte[] serializeLong(long fieldValue, int bytesCount, String fieldName) {
        checkArgument(bytesCount <= 8, String.format("Only integer fields with declared bytes size in [1, 8] are supported. "
                + "Field %s has %d bytes size.", fieldName, bytesCount));
        byte[] bytes = ByteBuffer.allocate(8).order(byteOrder).putLong(fieldValue).array();
        return copyArrayRange(bytes, BytesRange.newBuilder()
                .setFromByte(byteOrder.equals(ByteOrder.BIG_ENDIAN) ? 8 - bytesCount : 0)
                .setToByte(byteOrder.equals(ByteOrder.BIG_ENDIAN) ? 8 : bytesCount)
                .build());
    }

    private byte[] serializeBoolean(boolean fieldValue) {
        return new byte[]{fieldValue ? (byte) 1 : (byte) 0};
    }

    private byte[] serializeEnum(EnumValueDescriptor enumDescriptor, int bytesCount, String fieldName) {
        return serializeLong(enumDescriptor.getNumber(), bytesCount, fieldName);
    }

    private byte[] serializeByteString(ByteString fieldValue, int expectedBytesSize) throws CouldNotConvertMessageException {
        byte[] bytes = fieldValue.toByteArray();
        if (bytes.length != expectedBytesSize) {
            throw CouldNotConvertMessageException.serializeRequest(
                    "Actual byte string size %d is not equal to the declared field size %d", bytes.length, expectedBytesSize);
        }
        return bytes;
    }

    private long deserializeLong(byte[] bytes, String fieldName) {
        checkArgument(bytes.length <= 8, String.format("Only integer fields with declared bytes size in [1, 8] are supported. "
                + "Field %s has %d bytes size.", fieldName, bytes.length));
        byte[] longBytes = new byte[8];
        setBytesToArray(longBytes, byteOrder.equals(ByteOrder.BIG_ENDIAN) ? longBytes.length - bytes.length : 0, bytes);
        return ByteBuffer.wrap(longBytes).order(byteOrder).getLong();
    }

    private boolean deserializeBoolean(byte booleanValue) {
        return booleanValue != 0;
    }

    private ByteString deserializeByteString(byte[] stringBytes) {
        return ByteString.copyFrom(stringBytes);
    }

    private void setBytesToArray(byte[] bytesArray, int fromByte, byte[] bytesForSetting) {
        System.arraycopy(bytesForSetting, 0, bytesArray, fromByte, bytesForSetting.length);
    }

    private static byte[] copyArrayRange(byte[] array, BytesRange bytesRange) {
        byte[] arrayCopy = new byte[bytesRange.getToByte() - bytesRange.getFromByte()];
        System.arraycopy(array, bytesRange.getFromByte(), arrayCopy, 0, arrayCopy.length);
        return arrayCopy;
    }

    private static int getMessageBytesSize(Message message) {
        Descriptor descriptor = message.getDescriptorForType();
        MessageOptions options = descriptor.getOptions();
        checkArgument(options.hasExtension(Blerpc.messageSize) || descriptor.getFields().isEmpty(),
                String.format("Proto message \"%s\" with fields must have BytesSize option", descriptor.getName()));
        return options.getExtension(Blerpc.messageSize).getMessageSizeBytes();
    }

    private BytesRange getBytesRange(byte[] messageBytes, FieldDescriptor descriptor) {
        BytesRange bytesRange = getBytesRange(messageBytes.length, descriptor);
        checkBytesRangeNotFilled(messageBytes, bytesRange, descriptor.getName());
        return bytesRange;
    }

    private BytesRange getBytesRange(int messageBytesSize, FieldDescriptor descriptor) {
        checkFieldHasByteRangeOption(descriptor);
        BytesRange bytesRange = descriptor.getOptions().getExtension(Blerpc.filedBytes);
        checkBytesRangeValid(bytesRange, messageBytesSize, descriptor);
        return bytesRange;
    }

    private static void checkFieldHasByteRangeOption(FieldDescriptor descriptor) {
        checkArgument(descriptor.getOptions().hasExtension(Blerpc.filedBytes),
                String.format("Proto field \"%s\" must have ByteRange option", descriptor.getName()));
    }

    private static void checkBytesRangeValid(BytesRange bytesRange, int maxRangeValue, FieldDescriptor descriptor) {
        String name = descriptor.getName();
        int firstByte = bytesRange.getFromByte();
        int lastByte = bytesRange.getToByte();
        checkArgument(firstByte < lastByte, bytesRange.toString(),
                String.format("ByteRange first byte %d must be lower than last byte %d, field name: %s", firstByte, lastByte, name));
        checkArgument(firstByte >= 0,
                String.format("ByteRange must have only positive values, field name: %s", name));
        checkArgument(bytesRange.getToByte() <= maxRangeValue,
                String.format("ByteRange last byte %d must not be bigger than message byte count %d, field name: %s",
                        lastByte,
                        maxRangeValue,
                        name));
        checkBooleanRange(bytesRange, descriptor);
    }

    private static void checkBooleanRange(BytesRange bytesRange, FieldDescriptor descriptor) {
        if (!descriptor.getType().getJavaType().equals(JavaType.BOOLEAN)) {
            return;
        }
        checkArgument(bytesRange.getToByte() - bytesRange.getFromByte() == 1,
                String.format("Boolean value \"%s\" mustn't take more than 1 byte", descriptor.getName()));
    }

    private static void checkBytesRangeNotFilled(byte[] messageBytes, BytesRange bytesRange, String name) {
        byte[] byteArray = new byte[bytesRange.getToByte() - bytesRange.getFromByte()];
        checkArgument(Arrays.equals(copyArrayRange(messageBytes, bytesRange), byteArray),
                String.format("ByteRange must not intersect with other fields ByteRange, field name: %s", name));
    }
}
