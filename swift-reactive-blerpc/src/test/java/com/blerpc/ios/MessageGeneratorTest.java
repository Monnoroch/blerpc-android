package com.blerpc.ios;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.blerpc.proto.Blerpc;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.UnknownFieldSet;
import com.google.protobuf.compiler.PluginProtos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link MessageGenerator}. */
@RunWith(JUnit4.class)
public class MessageGeneratorTest {
    static final String FILE_NAME = "test_service";
    static final String SERVICE_NAME = "TestService";
    static final String PROTO_PACKAGE = "com.test";
    static final String SWIFT_PROTO_PACKAGE = "Com_Test_";
    static final String JAVA_PACKAGE = "com.test.proto";
    static final String SERVICE_JAVADOC = "Service javadoc.";
    static final String READ_METHOD_JAVADOC = "Read method javadoc.";
    static final String SUBSCRIBE_METHOD_JAVADOC = "Subscribe method javadoc.";
    static final String READ_METHOD_NAME = "ReadValue";
    static final String SUBSCRIBE_METHOD_NAME = "SubscribeValue";
    static final String METHOD_INPUT_TYPE = "TestInputValue";
    static final String METHOD_INPUT_TYPE_FULL_PATH = ".com.test.TestInputValue";
    static final String METHOD_OUTPUT_TYPE = "TestOutputValue";
    static final String METHOD_OUTPUT_TYPE_FULL_PATH = ".com.test.TestOutputValue";
    static final String MESSAGE_INT_NAME = "int_value";
    static final String MESSAGE_ENUM_NAME = "enum_value";
    static final String MESSAGE_FLOAT_NAME = "float_value";
    static final String SWIFT_TYPE_INT32 = "ProtoType.int32";
    private static final String SWIFT_TYPE_UNKNOWN = "ProtoType.unknown";
    static final String FILE_POSTFIX = "Extension";
    static final String FILE_EXTENSION = ".swift";
    static final DescriptorProtos.FieldOptions FIELD_OPTIONS =
            DescriptorProtos.FieldOptions.newBuilder()
                    .setUnknownFields(
                            UnknownFieldSet.newBuilder()
                                    .addField(Blerpc.FIELD_FIELD_NUMBER, UnknownFieldSet.Field.getDefaultInstance())
                                    .build())
                    .build();
    static final DescriptorProtos.FieldDescriptorProto FIELD_INT = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
            .setJsonName(MESSAGE_INT_NAME).setOptions(FIELD_OPTIONS).build();
    static final DescriptorProtos.FieldDescriptorProto FIELD_ENUM = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM)
            .setJsonName(MESSAGE_ENUM_NAME).setOptions(FIELD_OPTIONS).build();
    static final DescriptorProtos.FieldDescriptorProto FIELD_FLOAT = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT)
            .setJsonName(MESSAGE_FLOAT_NAME).setOptions(FIELD_OPTIONS).build();
    static final DescriptorProtos.DescriptorProto INPUT_MESSAGE_TYPE =
            DescriptorProtos.DescriptorProto.newBuilder()
                    .setName(METHOD_INPUT_TYPE).addField(0, FIELD_INT)
                    .addField(1, FIELD_ENUM).addField(2, FIELD_FLOAT).build();
    static final DescriptorProtos.DescriptorProto OUTPUT_MESSAGE_TYPE =
            DescriptorProtos.DescriptorProto.newBuilder().setName(METHOD_OUTPUT_TYPE).build();
    static final DescriptorProtos.FileOptions FILE_OPTIONS =
            DescriptorProtos.FileOptions.newBuilder()
                    .setJavaPackage(JAVA_PACKAGE)
                    .setJavaMultipleFiles(true)
                    .build();
    static final DescriptorProtos.SourceCodeInfo.Location SERVICE_LOCATION =
            DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                    .addAllPath(ImmutableList.of(6, 0))
                    .setLeadingComments(SERVICE_JAVADOC)
                    .build();
    static final DescriptorProtos.SourceCodeInfo.Location READ_METHOD_LOCATION =
            DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                    .addAllPath(ImmutableList.of(6, 0, 2, 0))
                    .setLeadingComments(READ_METHOD_JAVADOC)
                    .build();
    static final DescriptorProtos.SourceCodeInfo.Location SUBSCRIBE_METHOD_LOCATION =
            DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                    .addAllPath(ImmutableList.of(6, 0, 2, 1))
                    .setLeadingComments(SUBSCRIBE_METHOD_JAVADOC)
                    .build();
    static final DescriptorProtos.SourceCodeInfo FILE_SOURCE_CODE_INFO =
            DescriptorProtos.SourceCodeInfo.newBuilder()
                    .addLocation(SERVICE_LOCATION)
                    .addLocation(READ_METHOD_LOCATION)
                    .addLocation(SUBSCRIBE_METHOD_LOCATION)
                    .build();
    static final DescriptorProtos.ServiceOptions SERVICE_OPTIONS =
            DescriptorProtos.ServiceOptions.newBuilder()
                    .setUnknownFields(
                            UnknownFieldSet.newBuilder()
                                    .addField(Blerpc.SERVICE_FIELD_NUMBER, UnknownFieldSet.Field.getDefaultInstance())
                                    .build())
                    .build();
    static final DescriptorProtos.MethodDescriptorProto READ_METHOD =
            DescriptorProtos.MethodDescriptorProto.newBuilder()
                    .setName(READ_METHOD_NAME)
                    .setInputType(METHOD_INPUT_TYPE_FULL_PATH)
                    .setOutputType(METHOD_OUTPUT_TYPE_FULL_PATH)
                    .setServerStreaming(false)
                    .setClientStreaming(false)
                    .build();
    static final DescriptorProtos.MethodDescriptorProto SUBSCRIBE_METHOD =
            DescriptorProtos.MethodDescriptorProto.newBuilder()
                    .setName(SUBSCRIBE_METHOD_NAME)
                    .setInputType(METHOD_INPUT_TYPE_FULL_PATH)
                    .setOutputType(METHOD_OUTPUT_TYPE_FULL_PATH)
                    .setServerStreaming(true)
                    .setClientStreaming(false)
                    .build();
    static final DescriptorProtos.ServiceDescriptorProto SERVICE =
            DescriptorProtos.ServiceDescriptorProto.newBuilder()
                    .setOptions(SERVICE_OPTIONS)
                    .setName(SERVICE_NAME)
                    .addMethod(READ_METHOD)
                    .addMethod(SUBSCRIBE_METHOD)
                    .build();
    static final DescriptorProtos.FileDescriptorProto FILE =
            DescriptorProtos.FileDescriptorProto.newBuilder()
                    .setName(FILE_NAME)
                    .setPackage(PROTO_PACKAGE)
                    .setOptions(FILE_OPTIONS)
                    .setSourceCodeInfo(FILE_SOURCE_CODE_INFO)
                    .addService(SERVICE)
                    .addMessageType(INPUT_MESSAGE_TYPE)
                    .addMessageType(OUTPUT_MESSAGE_TYPE)
                    .build();
    static final PluginProtos.CodeGeneratorRequest REQUEST =
            PluginProtos.CodeGeneratorRequest.newBuilder()
                    .addFileToGenerate(FILE_NAME)
                    .addProtoFile(FILE)
                    .build();

    MessageGenerator generator;

    /** Set up. */
    @Before
    public void setUp() {
        generator = new MessageGenerator();
    }

    @Test
    public void buildMessageContexts() throws Exception {
        ImmutableList<MessageGenerator.MessageContext> messageList = generator.buildMessageContexts(REQUEST)
                .collect(toImmutableList());
        assertEquals(messageList, createMessageContext());
    }

    @Test
    public void buildServiceContexts_noFileToGenerate() throws Exception {
        PluginProtos.CodeGeneratorRequest request = REQUEST.toBuilder().clearFileToGenerate().build();
        ImmutableList<MessageGenerator.MessageContext> messageList = generator.buildMessageContexts(request)
                .collect(toImmutableList());
        assertThat(messageList).isEmpty();
    }

    @Test
    public void buildServiceContexts_UnknownType() throws Exception {
        ImmutableList<MessageGenerator.MessageContext> messageList = generator.buildMessageContexts(REQUEST)
                .collect(toImmutableList());
        assertThat(messageList).hasSize(2);
        MessageGenerator.MessageContext message = messageList.get(0);
        assertThat(message.fields).hasSize(3);
        MessageGenerator.FieldContext unknownField = message.fields.get(2);
        assertThat(unknownField.swiftType).isEqualTo(SWIFT_TYPE_UNKNOWN);
    }

    @Test
    public void buildServiceContexts_EnumType() throws Exception {
        ImmutableList<MessageGenerator.MessageContext> messageList = generator.buildMessageContexts(REQUEST)
                .collect(toImmutableList());
        assertThat(messageList).hasSize(2);
        MessageGenerator.MessageContext message = messageList.get(0);
        assertThat(message.fields).hasSize(3);
        MessageGenerator.FieldContext enumField = message.fields.get(1);
        assertThat(enumField.swiftType).isEqualTo(SWIFT_TYPE_INT32);
    }

    private MessageGenerator.MessageContext createMessageContext() {
        MessageGenerator.MessageContext messageContext =
                new MessageGenerator.MessageContext();
        messageContext.className = SERVICE_NAME;
        messageContext.fileName = METHOD_INPUT_TYPE + FILE_POSTFIX + FILE_EXTENSION;
        messageContext.packageName = JAVA_PACKAGE;
        messageContext.swiftPackageName = SWIFT_PROTO_PACKAGE;
        messageContext.className = METHOD_INPUT_TYPE + FILE_POSTFIX;
        messageContext.messageName = METHOD_INPUT_TYPE;

        MessageGenerator.FieldContext field = new MessageGenerator.FieldContext();
        field.name = MESSAGE_INT_NAME;
        field.type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32.name();
        field.swiftType = SWIFT_TYPE_INT32;
        field.isPrimitiveType = true;

        MessageGenerator.FieldContext fieldEnum = new MessageGenerator.FieldContext();
        fieldEnum.name = MESSAGE_ENUM_NAME;
        fieldEnum.type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM.name();
        fieldEnum.swiftType = SWIFT_TYPE_INT32;
        fieldEnum.isPrimitiveType = true;

        MessageGenerator.FieldContext unknownField = new MessageGenerator.FieldContext();
        unknownField.name = MESSAGE_FLOAT_NAME;
        unknownField.type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT.name();
        unknownField.swiftType = SWIFT_TYPE_UNKNOWN;
        unknownField.isPrimitiveType = true;

        messageContext.fields = ImmutableList.of(field, fieldEnum, unknownField);
        return messageContext;
    }

    private void assertEquals(
            ImmutableList<MessageGenerator.MessageContext> messageList,
            MessageGenerator.MessageContext message) {
        assertThat(messageList).hasSize(2);
        assertEquals(messageList.get(0), message);
    }

    private void assertEquals(
            MessageGenerator.MessageContext firstMessage,
            MessageGenerator.MessageContext secondMessage) {
        assertThat(firstMessage.className).isEqualTo(secondMessage.className);
        assertThat(firstMessage.fileName).isEqualTo(secondMessage.fileName);
        assertThat(firstMessage.packageName).isEqualTo(secondMessage.packageName);
        assertThat(firstMessage.swiftPackageName).isEqualTo(secondMessage.swiftPackageName);
        assertThat(firstMessage.className).isEqualTo(secondMessage.className);
        assertThat(firstMessage.messageName).isEqualTo(secondMessage.messageName);
        assertEquals(firstMessage.fields, secondMessage.fields);
    }

    private void assertEquals(
            ImmutableList<MessageGenerator.FieldContext> firstFieldList,
            ImmutableList<MessageGenerator.FieldContext> secondFieldList) {
        assertThat(firstFieldList).hasSize(3);
        assertThat(secondFieldList).hasSize(3);
        assertEquals(firstFieldList.get(0), secondFieldList.get(0));
    }

    private void assertEquals(
            MessageGenerator.FieldContext firstField,
            MessageGenerator.FieldContext secondField) {
        assertThat(firstField.name).isEqualTo(secondField.name);
        assertThat(firstField.type).isEqualTo(secondField.type);
        assertThat(firstField.swiftType).isEqualTo(secondField.swiftType);
        assertThat(firstField.fromByte).isEqualTo(secondField.fromByte);
        assertThat(firstField.toByte).isEqualTo(secondField.toByte);
        assertThat(firstField.isEnum).isEqualTo(secondField.isEnum);
        assertThat(firstField.isPrimitiveType).isEqualTo(secondField.isPrimitiveType);
        assertThat(firstField.isProtoObject).isEqualTo(secondField.isProtoObject);
    }
}
