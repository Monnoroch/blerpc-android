package com.blerpc.reactive;

import com.blerpc.proto.Blerpc;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.html.HtmlEscapers;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location;
import com.google.protobuf.compiler.PluginProtos;
import com.salesforce.jprotoc.Generator;
import com.salesforce.jprotoc.GeneratorException;
import com.salesforce.jprotoc.ProtoTypeMap;
import com.salesforce.jprotoc.ProtocPlugin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Protoc generator that generate RxJava wrappers for BleRpc services. */
public class ReactiveBleRpcGenerator extends Generator {

  /**
   * If {@link Location#getPathCount()} is equals to 4, then it identifies that current part of the
   * FileDescriptorProto is method.
   */
  private static final int METHOD_NUMBER_OF_PATHS = 4;
  /**
   * If {@link Location#getPathCount()} is equals to 2, then it identifies that current part of the
   * FileDescriptorProto is service.
   */
  private static final int SERVICE_NUMBER_OF_PATHS = 2;

  private static final String SERVICE_JAVADOC_PREFIX = "";
  private static final String METHOD_JAVADOC_PREFIX = "  ";
  private static final String CLASS_PREFIX = "Rx";
  private static final String RX_MUSTACHE_NAME = CLASS_PREFIX + "Stub.mustache";
  private static final String FACTORY_MUSTACHE_NAME = "FactoryService.mustache";

  public static void main(String[] args) {
    ProtocPlugin.generate(new ReactiveBleRpcGenerator());
  }

  @Override
  public Stream<PluginProtos.CodeGeneratorResponse.File> generate(
      PluginProtos.CodeGeneratorRequest request) throws GeneratorException {
    final ProtoTypeMap typeMap = ProtoTypeMap.of(request.getProtoFileList());

    List<FileDescriptorProto> protosToGenerate =
        request
            .getProtoFileList()
            .stream()
            .filter(protoFile -> request.getFileToGenerateList().contains(protoFile.getName()))
            .collect(Collectors.toList());

    List<ServiceContext> services = findServices(protosToGenerate, typeMap);
    Stream<PluginProtos.CodeGeneratorResponse.File> files = services.stream().map(this::buildFile);
    PluginProtos.CodeGeneratorResponse.File factory = PluginProtos.CodeGeneratorResponse.File.newBuilder()
            .setName("com/blerpc/reactive/BleServiceFactory.java")
            .setContent(generateFactoryFile(services))
            .build();
    return Stream.concat(files, Stream.of(factory));
  }

  private String readFileFromResources(String fileName) {
    StringBuilder result = new StringBuilder();
    try (Scanner scanner = new Scanner(getClass().getClassLoader().getResourceAsStream(fileName))) {
      while (scanner.hasNextLine()) {
        result.append(scanner.nextLine()).append("\n");
      }
      scanner.close();
    }
    return result.toString();
  }

  private List<ServiceContext> findServices(
      List<FileDescriptorProto> protos, ProtoTypeMap typeMap) {
    ImmutableList.Builder<ServiceContext> contexts = ImmutableList.builder();
    protos.forEach(fileProto -> contexts.addAll(findServicesInFile(fileProto, typeMap)));
    return contexts.build();
  }

  private List<ServiceContext> findServicesInFile(
      FileDescriptorProto fileProto, ProtoTypeMap typeMap) {
    List<Location> locations = fileProto.getSourceCodeInfo().getLocationList();
    return locations
        .stream()
        .filter(this::locationIsService)
        .filter(location -> isBleRpcService(fileProto, location))
        .map(location -> buildServiceContext(fileProto, locations, location, typeMap))
        .collect(Collectors.toList());
  }

  private boolean locationIsService(Location location) {
    return location.getPathCount() == SERVICE_NUMBER_OF_PATHS
        && location.getPath(0) == FileDescriptorProto.SERVICE_FIELD_NUMBER;
  }

  private boolean isBleRpcService(FileDescriptorProto fileProto, Location location) {
    return fileProto
        .getService(location.getPath(1))
        .getOptions()
        .getUnknownFields()
        .hasField(Blerpc.SERVICE_FIELD_NUMBER);
  }

  private ServiceContext buildServiceContext(
      FileDescriptorProto fileProto,
      List<Location> locations,
      Location location,
      ProtoTypeMap typeMap) {
    int serviceNumber = location.getPath(1);
    ServiceContext serviceContext =
        buildServiceContext(fileProto.getService(serviceNumber), typeMap, locations, serviceNumber);
    serviceContext.javaDoc = getJavaDoc(getComments(location), SERVICE_JAVADOC_PREFIX);
    serviceContext.protoName = fileProto.getName();
    serviceContext.packageName = extractPackageName(fileProto);
    return serviceContext;
  }

  private String extractPackageName(FileDescriptorProto proto) {
    FileOptions options = proto.getOptions();
    if (options != null) {
      String javaPackage = options.getJavaPackage();
      if (!Strings.isNullOrEmpty(javaPackage)) {
        return javaPackage;
      }
    }
    return Strings.nullToEmpty(proto.getPackage());
  }

  private ServiceContext buildServiceContext(
      ServiceDescriptorProto serviceProto,
      ProtoTypeMap typeMap,
      List<Location> locations,
      int serviceNumber) {
    ServiceContext serviceContext = new ServiceContext();
    serviceContext.fileName = CLASS_PREFIX + serviceProto.getName() + ".java";
    serviceContext.className = CLASS_PREFIX + serviceProto.getName();
    serviceContext.serviceName = serviceProto.getName();
    serviceContext.deprecated =
        serviceProto.getOptions() != null && serviceProto.getOptions().getDeprecated();

    locations
        .stream()
        .filter(location -> locationIsMethod(location, serviceNumber))
        .forEach(location -> buildMethodContext(serviceProto, serviceContext, location, typeMap));
    return serviceContext;
  }

  private boolean locationIsMethod(Location location, int serviceNumber) {
    return location.getPathCount() == METHOD_NUMBER_OF_PATHS
        && location.getPath(0) == FileDescriptorProto.SERVICE_FIELD_NUMBER
        && location.getPath(1) == serviceNumber
        && location.getPath(2) == ServiceDescriptorProto.METHOD_FIELD_NUMBER;
  }

  private void buildMethodContext(
      ServiceDescriptorProto serviceProto,
      ServiceContext serviceContext,
      Location location,
      ProtoTypeMap typeMap) {
    int methodNumber = location.getPath(METHOD_NUMBER_OF_PATHS - 1);
    MethodDescriptorProto methodProto = serviceProto.getMethod(methodNumber);
    MethodContext methodContext = new MethodContext();
    methodContext.methodName = lowerCaseFirst(methodProto.getName());
    methodContext.inputType = typeMap.toJavaTypeName(methodProto.getInputType());
    methodContext.outputType = typeMap.toJavaTypeName(methodProto.getOutputType());
    methodContext.deprecated =
        methodProto.getOptions() != null && methodProto.getOptions().getDeprecated();
    methodContext.isManyInput = methodProto.getClientStreaming();
    methodContext.isManyOutput = methodProto.getServerStreaming();
    methodContext.methodNumber = methodNumber;
    methodContext.javaDoc = getJavaDoc(getComments(location), METHOD_JAVADOC_PREFIX);
    serviceContext.methods.add(methodContext);
  }

  private String lowerCaseFirst(String s) {
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }

  private String generateFactoryFile(List<ServiceContext> services) {
    FileContext fileContext = new FileContext();
    fileContext.services = services;
    return applyTemplate(FACTORY_MUSTACHE_NAME, fileContext);
  }

  private PluginProtos.CodeGeneratorResponse.File buildFile(ServiceContext context) {
    String content = applyTemplate(RX_MUSTACHE_NAME, context);
    return PluginProtos.CodeGeneratorResponse.File.newBuilder()
        .setName(absoluteFileName(context))
        .setContent(content)
        .build();
  }

  private String buildCreateServiceMethod(ServiceContext context) {
    return applyTemplate(FACTORY_MUSTACHE_NAME, context);
  }

  private String absoluteFileName(ServiceContext ctx) {
    String dir = ctx.packageName.replace('.', '/');
    if (Strings.isNullOrEmpty(dir)) {
      return ctx.fileName;
    } else {
      return dir + "/" + ctx.fileName;
    }
  }

  private String getComments(Location location) {
    return location.getLeadingComments().isEmpty()
        ? location.getTrailingComments()
        : location.getLeadingComments();
  }

  private String getJavaDoc(String comments, String prefix) {
    if (!comments.isEmpty()) {
      StringBuilder builder = new StringBuilder("/**\n").append(prefix).append(" * <pre>\n");
      Arrays.stream(HtmlEscapers.htmlEscaper().escape(comments).split("\n"))
          .forEach(line -> builder.append(prefix).append(" * ").append(line).append("\n"));
      builder.append(prefix).append(" * <pre>\n").append(prefix).append(" */");
      return builder.toString();
    }
    return null;
  }

  /** Template class that describe protobuf file. */
  private static class FileContext {
    public List<ServiceContext> services = new ArrayList<>();
  }

  /** Template class that describe protobuf services. */
  private static class ServiceContext {
    public String fileName;
    public String protoName;
    public String packageName;
    public String className;
    public String serviceName;
    public boolean deprecated;
    public String javaDoc;
    public List<MethodContext> methods = new ArrayList<>();
  }

  /** Template class that describe protobuf methods. */
  private static class MethodContext {
    public String methodName;
    public String inputType;
    public String outputType;
    public boolean deprecated;
    public boolean isManyInput;
    public boolean isManyOutput;
    public int methodNumber;
    public String javaDoc;
  }
}
