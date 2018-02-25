package com.blerpc;

import com.blerpc.proto.Blerpc;
import com.blerpc.proto.BytesRange;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.FieldOptions;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;
import java.lang.reflect.Method;
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
     * Create {@link AnnotationMessageConverter} instance.
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
                    fieldBytes = serializeRequest(null, ((Message) entry.getValue()));
                    break;
                case INT:
                    fieldBytes = intToBytes(((Integer) fieldObject), fieldBytesSize, fieldName);
                    break;
                case LONG:
                    fieldBytes = intToBytes(((Long) fieldObject), fieldBytesSize, fieldName);
                    break;
                case BYTE_STRING:
                    fieldBytes = ((ByteString) entry.getValue()).toByteArray();
                    Preconditions.checkState(fieldBytes.length == fieldBytesSize,
                            String.format("ByteString \"%s\" size is not equals to field bytes count", fieldName));
                    break;
                case ENUM:
                    fieldBytes = intToBytes(((Descriptors.EnumValueDescriptor) fieldObject).getNumber(), fieldBytesSize, fieldName);
                    break;
                case BOOLEAN:
                    fieldBytes = new byte[]{(Boolean) entry.getValue() ? (byte) 1 : (byte) 0};
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
    public Message deserializeResponse(MethodDescriptor methodDescriptor, byte[] value) throws CouldNotConvertMessageException {
        return deserializeResponse(getMessageFromDescriptor(methodDescriptor.getOutputType()), value);
    }

    private Message deserializeResponse(Message message, byte[] value) throws CouldNotConvertMessageException {
        if (value.length == 0) {
            return message.getDefaultInstanceForType();
        }
        int messageBytesSize = getMessageBytesSize(message);
        Preconditions.checkState(value.length == messageBytesSize, "Message byte size is not equals to size of device response");

        Message.Builder messageBuilder = message.toBuilder();
        for (FieldDescriptor fieldDescriptor : message.getDescriptorForType().getFields()) {
            BytesRange bytesRange = getBytesRange(messageBytesSize, fieldDescriptor);
            String fieldName = fieldDescriptor.getName();
            JavaType fieldType = fieldDescriptor.getType().getJavaType();

            switch (fieldType) {
                case MESSAGE:
                    messageBuilder.setField(fieldDescriptor, deserializeResponse(getMessageFromDescriptor(
                            fieldDescriptor.getMessageType()), copyArrayRange(value, bytesRange)));
                    break;
                case INT:
                    messageBuilder.setField(fieldDescriptor, (int) bytesToInt(copyArrayRange(value, bytesRange), fieldName));
                    break;
                case LONG:
                    messageBuilder.setField(fieldDescriptor, bytesToInt(copyArrayRange(value, bytesRange), fieldName));
                    break;
                case BYTE_STRING:
                    messageBuilder.setField(fieldDescriptor, ByteString.copyFrom(copyArrayRange(value, bytesRange)));
                    break;
                case ENUM:
                    messageBuilder.setField(fieldDescriptor, fieldDescriptor.getEnumType()
                            .findValueByNumber((int) bytesToInt(copyArrayRange(value, bytesRange), fieldName)));
                    break;
                case BOOLEAN:
                    messageBuilder.setField(fieldDescriptor, value[bytesRange.getFromByte()] != 0);
                    break;
                // TODO(#5): Add support of String, Float and Double.
                default:
                    throw CouldNotConvertMessageException.serializeRequest(String.format("Unsupported field type: %s", fieldType.name()));
            }
        }
        return messageBuilder.build();
    }

    private byte[] intToBytes(long fieldValue, int bytesCount, String fieldName) throws CouldNotConvertMessageException {
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
                throw CouldNotConvertMessageException.serializeRequest(String.format("Number field \"%s\" has wrong bytes count", fieldName));
        }
    }

    private long bytesToInt(byte[] intBytes, String fieldName) throws CouldNotConvertMessageException {
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
                throw CouldNotConvertMessageException.serializeRequest(String.format("Number field \"%s\" has wrong bytes count", fieldName));
        }
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
        Preconditions.checkState(options.hasExtension(Blerpc.message) || descriptor.getFields().isEmpty(),
                String.format("Proto message \"%s\" with fields must have BytesSize option", descriptor.getName()));
        return options.getExtension(Blerpc.message).getMessageSizeBytes();
    }

    private BytesRange getBytesRange(byte[] messageBytes, FieldDescriptor descriptor) {
        FieldOptions options = descriptor.getOptions();
        String fieldName = descriptor.getName();
        Preconditions.checkState(options.hasExtension(Blerpc.field),
                String.format("Proto field \"%s\" must have ByteRange option", fieldName));
        BytesRange bytesRange = options.getExtension(Blerpc.field);
        checkBytesRangeValid(bytesRange, messageBytes.length, descriptor);
        checkBytesRangeNotFilled(messageBytes, bytesRange, fieldName);
        return bytesRange;
    }

    private BytesRange getBytesRange(int messageBytesSize, FieldDescriptor descriptor) {
        Preconditions.checkState(descriptor.getOptions().hasExtension(Blerpc.field),
                String.format("Proto field \"%s\" must have ByteRange option", descriptor.getName()));
        BytesRange bytesRange = descriptor.getOptions().getExtension(Blerpc.field);
        checkBytesRangeValid(bytesRange, messageBytesSize, descriptor);
        return bytesRange;
    }

    private static void checkBytesRangeValid(BytesRange bytesRange, int maxRangeValue, FieldDescriptor descriptor) {
        String name = descriptor.getName();
        Preconditions.checkState(bytesRange.getFromByte() < bytesRange.getToByte(), bytesRange.toString(),
                String.format("ByteRange beginning index must be lower than ending index, field name: %s", name));
        Preconditions.checkState(bytesRange.getFromByte() >= 0,
                String.format("ByteRange must have only positive values, field name: %s", name));
        Preconditions.checkState(bytesRange.getToByte() <= maxRangeValue,
                String.format("ByteRange ending index must not be bigger than message byte count, field name: %s", name));
        checkBooleanRange(bytesRange, descriptor);
    }

    private static void checkBooleanRange(BytesRange bytesRange, FieldDescriptor descriptor) {
        if (!descriptor.getType().getJavaType().equals(JavaType.BOOLEAN)) {
            return;
        }
        Preconditions.checkState(bytesRange.getToByte() - bytesRange.getFromByte() == 1,
                String.format("Boolean value \"%s\" mustn't take more than 1 byte", descriptor.getName()));
    }

    private static void checkBytesRangeNotFilled(byte[] messageBytes, BytesRange bytesRange, String name) {
        byte[] byteArray = new byte[bytesRange.getToByte() - bytesRange.getFromByte()];
        Preconditions.checkState(Arrays.equals(copyArrayRange(messageBytes, bytesRange), byteArray),
                String.format("ByteRange must not intersect with other fields ByteRange, field name: %s", name));
    }

    private static Message getMessageFromDescriptor(Descriptor descriptor) throws CouldNotConvertMessageException {
        try {
            Class<?> clazz = Class.forName(descriptor.getFullName());
            Method method = clazz.getDeclaredMethod("newBuilder", null);
            return ((Message.Builder) method.invoke(null)).build();
        } catch (Exception exception) {
            throw CouldNotConvertMessageException.deserializeResponse(exception);
        }
    }
}
