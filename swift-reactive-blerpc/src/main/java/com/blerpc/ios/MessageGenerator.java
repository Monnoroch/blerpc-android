package com.blerpc.ios;

import com.blerpc.proto.Blerpc;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.compiler.PluginProtos;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MessageGenerator {

    private static final String OUTPUT_CLASS_POSTFIX = "Extension";
    private static final String OUTPUT_FILE_EXTENSION = ".swift";

    public Stream<MessageContext> buildMessageContexts(PluginProtos.CodeGeneratorRequest request) {
        return request
                .getProtoFileList()
                .stream()
                .filter(this::hasPackage)
                .filter(file -> request.getFileToGenerateList().contains(file.getName()))
                .flatMap(this::buildMessageContext);
    }

    private Stream<MessageContext> buildMessageContext(DescriptorProtos.FileDescriptorProto protoFile) {
        return protoFile.getMessageTypeList().stream().flatMap(messageType ->
                generateMessageInformation(messageType, protoFile)
        );
    }

    private Stream<MessageContext> generateMessageInformation(DescriptorProtos.DescriptorProto messageType,
                                                              DescriptorProtos.FileDescriptorProto protoFile) {
        MessageContext messageContext = new MessageContext();
        messageContext.swiftPackageName = extractSwiftPackageName(protoFile);
        messageContext.packageName = extractPackageName(protoFile);
        messageContext.serviceName = messageType.getName();
        messageContext.className = messageContext.serviceName + OUTPUT_CLASS_POSTFIX;
        messageContext.fileName = messageContext.className + OUTPUT_FILE_EXTENSION;
        messageContext.fields = messageType.getFieldList().stream().flatMap(fieldType ->
                generateFieldsInformation(fieldType, protoFile)
        ).collect(ImmutableList.toImmutableList());
        return Stream.of(messageContext);
    }

    private Stream<FieldContext> generateFieldsInformation(DescriptorProtos.FieldDescriptorProto field,
                                                      DescriptorProtos.FileDescriptorProto protoFile) {
        FieldContext fieldContext = new FieldContext();
        fieldContext.name = field.getJsonName().replace("Id", "ID"); // to conform output swift rules
        fieldContext.type = field.getType().toString();
        fieldContext.protoType = field.getTypeName().replace(protoFile.getPackage(), "")
                .replace(".", "");

        if (fieldContext.type.equals("TYPE_ENUM")) {
            fieldContext.isEnum = true;
            fieldContext.isProtoObject = false;
            fieldContext.isPrimitiveType = false;
        } else if (fieldContext.type.equals("TYPE_MESSAGE")) {
            fieldContext.isEnum = false;
            fieldContext.isProtoObject = true;
            fieldContext.isPrimitiveType = false;
        } else {
            fieldContext.isEnum = false;
            fieldContext.isProtoObject = false;
            fieldContext.isPrimitiveType = true;
        }

        switch (fieldContext.type) {
            case "TYPE_INT32":
                fieldContext.swiftType = "ProtoType.int32";
                break;
            case  "TYPE_BYTES":
                fieldContext.swiftType = "ProtoType.byte";
                break;
            case  "TYPE_BOOL":
                fieldContext.swiftType = "ProtoType.bool";
                break;
            default:
                fieldContext.swiftType = "ProtoType.unknown";
                break;
        }

        fieldContext.toByte = field.getOptions().getExtension(Blerpc.field).getToByte();
        fieldContext.fromByte = field.getOptions().getExtension(Blerpc.field).getFromByte();
        return Stream.of(fieldContext);
    }

    private boolean hasPackage(DescriptorProtos.FileDescriptorProto file) {
        return !file.getPackage().isEmpty();
    }

    private String extractPackageName(DescriptorProtos.FileDescriptorProto proto) {
        String javaPackage = proto.getOptions().getJavaPackage();
        return !javaPackage.isEmpty() ? javaPackage : proto.getPackage();
    }

    private String extractSwiftPackageName(DescriptorProtos.FileDescriptorProto proto) {
        String packageName = proto.getPackage();
        String[] splittedPackageName = packageName.split("\\.");
        return Arrays.stream(splittedPackageName).map(key -> this.upperCaseFirstLetter(key) + "_").collect(Collectors.joining());
    }

    private String upperCaseFirstLetter(String string) {
        return Character.toUpperCase(string.charAt(0)) + string.substring(1);
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
