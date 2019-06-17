package com.blerpc.ios;

import com.blerpc.proto.Blerpc;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import java.util.Arrays;
import java.util.stream.Collectors;
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
    private static final String SWIFT_TYPE_INT32 = "ProtoType.int32";
    private static final String SWIFT_TYPE_BYTES = "ProtoType.byte";
    private static final String SWIFT_TYPE_BOOL = "ProtoType.bool";
    private static final String SWIFT_TYPE_UNKNOWN = "ProtoType.unknown";
    private static final String SWIFT_PACKAGE_TO_CLASS_SAPARATOR = "_";
    private static final String PROTO_PACKAGE_SEPARATOR = "\\.";

    /**
     * Builds message contexts based on input proto file request.
     * @param request - input request (usually as input proto file).
     * @return prepared service contexts parsed from input proto request.
     */
    public Stream<MessageContext> buildMessageContexts(CodeGeneratorRequest request) {
        return request
                .getProtoFileList()
                .stream()
                .filter(this::hasPackage)
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
        messageContext.swiftPackageName = extractSwiftPackageName(protoFile);
        messageContext.packageName = extractPackageName(protoFile);
        messageContext.serviceName = messageType.getName();
        messageContext.className = messageContext.serviceName + OUTPUT_CLASS_POSTFIX;
        messageContext.fileName = messageContext.className + OUTPUT_FILE_EXTENSION;
        messageContext.fields = messageType.getFieldList().stream().map(fieldType ->
                generateFieldsInformation(fieldType, protoFile)
        ).collect(ImmutableList.toImmutableList());
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
            case PROTO_TYPE_INT32:
                fieldContext.swiftType = SWIFT_TYPE_INT32;
                break;
            case  PROTO_TYPE_BYTES:
                fieldContext.swiftType = SWIFT_TYPE_BYTES;
                break;
            case  PROTO_TYPE_BOOL:
                fieldContext.swiftType = SWIFT_TYPE_BOOL;
                break;
            default:
                fieldContext.swiftType = SWIFT_TYPE_UNKNOWN;
                break;
        }

        fieldContext.toByte = field.getOptions().getExtension(Blerpc.field).getToByte();
        fieldContext.fromByte = field.getOptions().getExtension(Blerpc.field).getFromByte();
        return fieldContext;
    }

    private boolean hasPackage(FileDescriptorProto file) {
        return !file.getPackage().isEmpty();
    }

    private String extractPackageName(FileDescriptorProto proto) {
        String javaPackage = proto.getOptions().getJavaPackage();
        return javaPackage.isEmpty() ? proto.getPackage() : javaPackage;
    }

    private String extractSwiftPackageName(FileDescriptorProto proto) {
        String packageName = proto.getPackage();
        String[] splittedPackageName = packageName.split(PROTO_PACKAGE_SEPARATOR);
        return Arrays.stream(splittedPackageName)
                .map(key -> upperCaseFirstLetter(key) + SWIFT_PACKAGE_TO_CLASS_SAPARATOR)
                .collect(Collectors.joining());
    }

    private String upperCaseFirstLetter(String string) {
        return Character.toUpperCase(string.charAt(0)) + (string.length() > 1 ? string.substring(1) : "");
    }

    /** Template class that describe protobuf message. */
    @SuppressWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    @VisibleForTesting
    public static class MessageContext {
        public String fileName;
        public String packageName;
        public String swiftPackageName;
        public String className;
        public String serviceName;
        public ImmutableList<FieldContext> fields = ImmutableList.of();
    }

    /** Template class that describe protobuf method's fields. */
    @SuppressWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    @VisibleForTesting
    public static class FieldContext {
        public String type;
        public String swiftType;
        public boolean isEnum;
        public boolean isProtoObject;
        public boolean isPrimitiveType;
        public String protoType;
        public String name;
        public int toByte;
        public int fromByte;
    }
}
