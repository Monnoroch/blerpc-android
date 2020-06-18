package com.blerpc.ios;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.blerpc.proto.Blerpc;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import java.util.stream.Stream;

/** Generator which generates message encoding/decoding Swift extensions. */
public class MessageGenerator {

    private static final String OUTPUT_CLASS_POSTFIX = "Extension";
    private static final String OUTPUT_FILE_EXTENSION = ".swift";
    private static final String PROTO_ID_NAME = "Id";
    private static final String SWIFT_ID_NAME = "ID";
    private static final String TYPE_ENUM = "TYPE_ENUM";
    private static final String TYPE_MESSAGE = "TYPE_MESSAGE";
    private static final String PROTO_TYPE_INT32 = "TYPE_INT32";
    private static final String PROTO_TYPE_BYTES = "TYPE_BYTES";
    private static final String PROTO_TYPE_BOOL = "TYPE_BOOL";
    private static final String PROTO_TYPE_FLOAT = "TYPE_FLOAT";
    private static final String SWIFT_TYPE_INT32 = "ProtoType.int32";
    private static final String SWIFT_TYPE_BYTES = "ProtoType.byte";
    private static final String SWIFT_TYPE_BOOL = "ProtoType.bool";
    private static final String SWIFT_TYPE_FLOAT = "ProtoType.float";
    private static final String SWIFT_TYPE_UNKNOWN = "ProtoType.unknown";

    /**
     * Builds message contexts based on input proto file request.
     * @param request - input request (usually as input proto file).
     * @return prepared service contexts parsed from input proto request.
     */
    public Stream<MessageContext> buildMessageContexts(CodeGeneratorRequest request) {
        return request
                .getProtoFileList()
                .stream()
                .filter(Common::hasPackage)
                .filter(file -> request.getFileToGenerateList().contains(file.getName()))
                .flatMap(this::buildMessageContext);
    }

    private Stream<MessageContext> buildMessageContext(FileDescriptorProto protoFile) {
        return protoFile.getMessageTypeList().stream().map(messageType ->
                generateMessageInformation(messageType, protoFile)
        );
    }

    private MessageContext generateMessageInformation(DescriptorProto messageType,
                                                              FileDescriptorProto protoFile) {
        MessageContext messageContext = new MessageContext();
        messageContext.swiftPackageName = Common.extractSwiftPackageName(protoFile);
        messageContext.packageName = Common.extractPackageName(protoFile);
        messageContext.messageName = messageType.getName();
        messageContext.className = messageContext.messageName + OUTPUT_CLASS_POSTFIX;
        messageContext.fileName = messageContext.className + OUTPUT_FILE_EXTENSION;
        messageContext.fields = messageType.getFieldList().stream().map(fieldType ->
                generateFieldsInformation(fieldType, protoFile)
        ).collect(toImmutableList());
        return messageContext;
    }

    private FieldContext generateFieldsInformation(FieldDescriptorProto field,
                                                      FileDescriptorProto protoFile) {
        FieldContext fieldContext = new FieldContext();
        fieldContext.name = field.getJsonName().replace(PROTO_ID_NAME, SWIFT_ID_NAME); // to conform output swift rules
        fieldContext.type = field.getType().toString();
        fieldContext.protoType = field.getTypeName().replace(protoFile.getPackage(), "")
                .replace(".", "");
        fieldContext.isEnum = fieldContext.type.equals(TYPE_ENUM);
        fieldContext.isProtoObject = fieldContext.type.equals(TYPE_MESSAGE);
        fieldContext.isPrimitiveType = !fieldContext.isEnum && !fieldContext.isProtoObject;

        switch (fieldContext.type) {
            case TYPE_ENUM:
            case PROTO_TYPE_INT32:
                fieldContext.swiftType = SWIFT_TYPE_INT32;
                break;
            case  PROTO_TYPE_BYTES:
                fieldContext.swiftType = SWIFT_TYPE_BYTES;
                break;
            case  PROTO_TYPE_BOOL:
                fieldContext.swiftType = SWIFT_TYPE_BOOL;
                break;
            case PROTO_TYPE_FLOAT:
                fieldContext.swiftType = SWIFT_TYPE_FLOAT;
                break;
            default:
                fieldContext.swiftType = SWIFT_TYPE_UNKNOWN;
                break;
        }

        fieldContext.toByte = field.getOptions().getExtension(Blerpc.field).getToByte();
        fieldContext.fromByte = field.getOptions().getExtension(Blerpc.field).getFromByte();
        return fieldContext;
    }

    /** Template class that describe protobuf message. */
    @SuppressWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    @VisibleForTesting
    public static class MessageContext {
        public String fileName;
        public String packageName;
        public String swiftPackageName;
        public String className;
        public String messageName;
        public ImmutableList<FieldContext> fields = ImmutableList.of();
    }

    /** Template class that describe protobuf method's fields. */
    @SuppressWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    @VisibleForTesting
    public static class FieldContext {
        public String type;
        public String swiftType;
        public String protoType;
        public String name;
        public int toByte;
        public int fromByte;
        public boolean isEnum;
        public boolean isProtoObject;
        public boolean isPrimitiveType;
    }
}
