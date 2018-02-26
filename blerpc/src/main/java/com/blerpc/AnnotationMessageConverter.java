package com.blerpc;

import static com.google.common.base.Preconditions.checkArgument;

import com.blerpc.proto.Blerpc;
import com.blerpc.proto.BytesRange;
import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
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
            int fieldBytesSize = bytesRange.getToByte() - bytesRange.getFromByte();
            JavaType fieldType = fieldDescriptor.getType().getJavaType();
            byte[] fieldBytes;
            switch (fieldType) {
                case MESSAGE:
                    fieldBytes = serializeRequest(null, (Message) entry.getValue());
                    break;
                case INT:
                    fieldBytes = serializeLong((Integer) fieldObject, fieldBytesSize, fieldName);
                    break;
                case LONG:
                    fieldBytes = serializeLong((Long) fieldObject, fieldBytesSize, fieldName);
                    break;
                case BYTE_STRING:
                    fieldBytes = serializeByteString((ByteString) entry.getValue(), fieldBytesSize);
                    break;
                case ENUM:
                    fieldBytes = serializeLong(((Descriptors.EnumValueDescriptor) fieldObject).getNumber(), fieldBytesSize, fieldName);
                    break;
                case BOOLEAN:
                    fieldBytes = serializeBoolean((Boolean) entry.getValue());
                    break;
                // TODO(#5): Add support of String, Float and Double.
                default:
                    throw CouldNotConvertMessageException.serializeRequest(String.format("Unsupported field type: %s", fieldType.name()));
            }
            setBytesToArray(messageBytes, bytesRange.getFromByte(), fieldBytes);
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

    private byte[] serializeLong(long fieldValue, int bytesCount, String fieldName) throws CouldNotConvertMessageException {
        switch (bytesCount) {
            case 1:
                return new byte[]{(byte) fieldValue};
            case 2:
                return ByteBuffer.allocate(2).order(byteOrder).putShort((short) fieldValue).array();
            case 4:
                return ByteBuffer.allocate(4).order(byteOrder).putInt((int) fieldValue).array();
            case 8:
                return ByteBuffer.allocate(8).order(byteOrder).putLong(fieldValue).array();
            default:
                throw CouldNotConvertMessageException
                        .serializeRequest(String.format("Number field \"%s\" has wrong bytes count %d", fieldName, bytesCount));
        }
    }

    private byte[] serializeBoolean(boolean fieldValue) throws CouldNotConvertMessageException {
        return new byte[]{fieldValue ? (byte) 1 : (byte) 0};
    }

    private byte[] serializeByteString(ByteString fieldValue, int expectedBytesSize) throws CouldNotConvertMessageException {
        byte[] bytes = fieldValue.toByteArray();
        if (bytes.length != expectedBytesSize) {
            throw CouldNotConvertMessageException.serializeRequest
                    ("Actual byte string size %d is not equal to the declared field size %d", bytes.length, expectedBytesSize);
        }
        return bytes;
    }

    private long deserializeLong(byte[] intBytes, String fieldName) throws CouldNotConvertMessageException {
        switch (intBytes.length) {
            case 1:
                return intBytes[0];
            case 2:
                return ByteBuffer.wrap(intBytes).order(byteOrder).getShort();
            case 4:
                return ByteBuffer.wrap(intBytes).order(byteOrder).getInt();
            case 8:
                return ByteBuffer.wrap(intBytes).order(byteOrder).getLong();
            default:
                throw CouldNotConvertMessageException
                        .serializeRequest(String.format("Number field \"%s\" has wrong bytes count %d", fieldName, intBytes.length));
        }
    }

    private boolean deserializeBoolean(byte booleanValue) throws CouldNotConvertMessageException {
        return booleanValue != 0;
    }

    private ByteString deserializeByteString(byte[] stringBytes) throws CouldNotConvertMessageException {
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
