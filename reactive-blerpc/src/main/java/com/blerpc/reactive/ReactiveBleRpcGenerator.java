package com.blerpc.reactive;

import com.blerpc.proto.Blerpc;
import com.google.common.base.Strings;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Protoc generator that generate RxJava wrappers for BleRpc services. */
public class ReactiveBleRpcGenerator extends Generator {

  /**
   * In proto file each element have number type and index: [6, 2]. If there is one element [4, 0]
   * in another [6, 2], then for describing it we should show path to it like this: [6, 2, 4, 0]
   *
   * <p>In this case, first number (6) is number type of service type -
   * FileDescriptorProto.SERVICE_FIELD_NUMBER. Second number (2) is index of service in file. Third
   * number (4) is number type of method type - FileDescriptorProto.METHOD_FIELD_NUMBER. Forth
   * number (0) is index of method in service.
   *
   * <p>To be brief, [6, 2, 4, 0] shows, that our location is first method in third service in the
   * file. Thus, any method has 4 elements in path.
   *
   * <p>For describing service we need only to elements in path: [6, 2]. Path shows, that location
   * is third service in the file.
   */
  private static final int METHOD_NUMBER_OF_PATHS = 4;

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

    Stream<FileDescriptorProto> protosToGenerate =
        request
            .getProtoFileList()
            .stream()
            .filter(protoFile -> request.getFileToGenerateList().contains(protoFile.getName()));

    Stream<ServiceContext> services = findServices(protosToGenerate, typeMap);
    PluginProtos.CodeGeneratorResponse.File factory =
        PluginProtos.CodeGeneratorResponse.File.newBuilder()
            .setName("com/blerpc/reactive/BleServiceFactory.java")
            .setContent(generateFactoryFile(services))
            .build();
    return Stream.concat(services.map(this::buildServiceFile), Stream.of(factory));
  }

  private Stream<ServiceContext> findServices(
      Stream<FileDescriptorProto> protos, ProtoTypeMap typeMap) {
    return protos.flatMap(
        (Function<FileDescriptorProto, Stream<ServiceContext>>)
            fileProto -> findServicesInFile(fileProto, typeMap));
  }

  private Stream<ServiceContext> findServicesInFile(
      FileDescriptorProto fileProto, ProtoTypeMap typeMap) {
    List<Location> locations = fileProto.getSourceCodeInfo().getLocationList();
    return locations
        .stream()
        .filter(this::locationIsService)
        .filter(location -> isBleRpcService(fileProto, location))
        .map(location -> buildServiceContext(fileProto, locations, location, typeMap));
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
    serviceContext.javaDoc = getJavaDoc(location.getLeadingComments(), SERVICE_JAVADOC_PREFIX);
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
    methodContext.javaDoc = getJavaDoc(location.getLeadingComments(), METHOD_JAVADOC_PREFIX);
    serviceContext.methods.add(methodContext);
  }

  private String lowerCaseFirst(String string) {
    return Character.toLowerCase(string.charAt(0)) + string.substring(1);
  }

  private String generateFactoryFile(Stream<ServiceContext> services) {
    FileContext fileContext = new FileContext();
    fileContext.services = services.collect(Collectors.toList());
    return applyTemplate(FACTORY_MUSTACHE_NAME, fileContext);
  }

  private PluginProtos.CodeGeneratorResponse.File buildServiceFile(ServiceContext context) {
    return PluginProtos.CodeGeneratorResponse.File.newBuilder()
        .setName(absoluteFileName(context))
        .setContent(applyTemplate(RX_MUSTACHE_NAME, context))
        .build();
  }

  private String absoluteFileName(ServiceContext context) {
    String dir = context.packageName.replace('.', '/');
    if (Strings.isNullOrEmpty(dir)) {
      return context.fileName;
    } else {
      return dir + "/" + context.fileName;
    }
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
