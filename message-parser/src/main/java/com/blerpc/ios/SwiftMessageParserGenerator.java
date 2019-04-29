package com.blerpc.ios;

import com.blerpc.proto.Blerpc;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.compiler.PluginProtos;
import com.salesforce.jprotoc.Generator;
import com.salesforce.jprotoc.GeneratorException;
import com.salesforce.jprotoc.ProtocPlugin;

import edu.umd.cs.findbugs.annotations.SuppressWarnings;

import java.io.File;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Protoc generator that generate Swift wrappers for messages withing service file. */
public class SwiftMessageParserGenerator extends Generator {

  private static final String OUTPUT_CLASS_POSTFIX = "Extension";
  private static final String OUTPUT_FILE_EXTENSION = ".swift";
  private static final String TEMPLATE_FILE = "SwiftMessageExtension.mustache";

  public static void main(String[] args) {
    ArrayList<Generator> generators = new ArrayList<>();
    generators.add(new SwiftMessageParserGenerator());

    ArrayList<GeneratedMessage.GeneratedExtension> generatorsExtensions = new ArrayList<>();
    generatorsExtensions.add(Blerpc.message);
    generatorsExtensions.add(Blerpc.field);
    generatorsExtensions.add(Blerpc.characteristic);

    ProtocPlugin.generate(generators, generatorsExtensions);
  }

  // TODO(#36): add validation for requests and responses.
  @Override
  public Stream<PluginProtos.CodeGeneratorResponse.File> generate(
      PluginProtos.CodeGeneratorRequest request) throws GeneratorException {
    Stream<MessageContext> messages = buildMessageContexts(request);
    return messages.map(this::buildOutputFile);
  }

  @VisibleForTesting
  private Stream<MessageContext> buildMessageContexts(PluginProtos.CodeGeneratorRequest request) {
    return request
        .getProtoFileList()
        .stream()
        .filter(this::hasPackage)
        .filter(file -> request.getFileToGenerateList().contains(file.getName()))
        .flatMap(this::getFileLocations)
        .map(
            fileLocation ->
                    buildMessageContext(
                    fileLocation.getKey())).findFirst().get();
  }

  private Stream<MessageContext> buildMessageContext(FileDescriptorProto protoFile) {
    List<MessageContext> arrayMessages = new ArrayList<MessageContext>();

    for (int i = 0; i < protoFile.getMessageTypeCount(); i++) {
      DescriptorProtos.DescriptorProto serviceProto = protoFile.getMessageType(i);
      MessageContext messageContext = new MessageContext();
      messageContext.swiftPackageName = extractSwiftPackageName(protoFile);
      messageContext.packageName = extractPackageName(protoFile);
      messageContext.serviceName = serviceProto.getName();
      messageContext.className = messageContext.serviceName + OUTPUT_CLASS_POSTFIX;
      messageContext.fileName = messageContext.className + OUTPUT_FILE_EXTENSION;

      List<FieldContext> arrayFields = new ArrayList<FieldContext>();

      for (DescriptorProtos.FieldDescriptorProto field : serviceProto.getFieldList()) {
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

        fieldContext.toByte = field.getOptions().getExtension(Blerpc.field).getToByte();
        fieldContext.fromByte = field.getOptions().getExtension(Blerpc.field).getFromByte();
        arrayFields.add(fieldContext);
      }

      messageContext.fields = ImmutableList.copyOf(arrayFields);
      arrayMessages.add(messageContext);
    }

    return arrayMessages.stream();
  }

  private PluginProtos.CodeGeneratorResponse.File buildOutputFile(MessageContext context) {
    return PluginProtos.CodeGeneratorResponse.File.newBuilder()
        .setName(fullFileName(context))
        .setContent(applyTemplate(TEMPLATE_FILE, context))
        .build();
  }

  private Stream<AbstractMap.SimpleEntry<FileDescriptorProto, Location>> getFileLocations(
          FileDescriptorProto file) {
    return file.getSourceCodeInfo()
            .getLocationList()
            .stream()
            .map(location -> new AbstractMap.SimpleEntry<>(file, location));
  }

  private String extractPackageName(FileDescriptorProto proto) {
    String javaPackage = proto.getOptions().getJavaPackage();
    return !javaPackage.isEmpty() ? javaPackage : proto.getPackage();
  }

  private String extractSwiftPackageName(FileDescriptorProto proto) {
    String packageName = proto.getPackage();
    String[] splittedPackageName = packageName.split("\\.");
    String swiftPackageName = "";

    for (String substring : splittedPackageName) {
      swiftPackageName += upperCaseFirstLetter(substring) + "_";
    }

    return swiftPackageName;
  }

  private String upperCaseFirstLetter(String string) {
    return Character.toUpperCase(string.charAt(0)) + string.substring(1);
  }

  private String fullFileName(MessageContext context) {
    return Paths.get(context.packageName.replace(".", File.separator), context.fileName).toString();
  }

  private boolean hasPackage(FileDescriptorProto file) {
    return !file.getPackage().isEmpty();
  }

  /** Template class that describe protobuf message. */
  @SuppressWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
  @VisibleForTesting
  static class MessageContext {
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
  static class FieldContext {
    public String type;
    public boolean isEnum;
    public boolean isProtoObject;
    public boolean isPrimitiveType;
    public String protoType;
    public String name;
    public int toByte;
    public int fromByte;
  }
}
