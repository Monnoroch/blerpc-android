package com.blerpc.reactive;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import com.blerpc.proto.Blerpc;
import com.google.common.annotations.VisibleForTesting;
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
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import java.io.File;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nullable;

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
   *
   * <p>More details at
   * https://developers.google.com/protocol-buffers/docs/reference/java/com/google/protobuf/DescriptorProtos.SourceCodeInfo.Location#getPathCount--
   */
  private static final int METHOD_NUMBER_OF_PATHS = 4;

  private static final int SERVICE_NUMBER_OF_PATHS = 2;
  private static final String SERVICE_JAVADOC_PREFIX = "";
  private static final String METHOD_JAVADOC_PREFIX = "  ";
  private static final String RX_CLASS_PREFIX = "Rx";
  private static final String JAVA_SOURCE_EXTENSION = ".java";
  private static final String RX_SERVICE_TEMPLATE_FILE = RX_CLASS_PREFIX + "Stub.mustache";
  private static final String FACTORY_TEMPLATE_FILE = "FactoryService.mustache";
  private static final String SERVICE_FACTORY_PATH =
      Paths.get("com", "blerpc", "reactive", "BleServiceFactory.java").toString();

  public static void main(String[] args) {
    ProtocPlugin.generate(new ReactiveBleRpcGenerator());
  }

  // TODO(#36): add validation for requests and responses.
  @Override
  public Stream<PluginProtos.CodeGeneratorResponse.File> generate(
      PluginProtos.CodeGeneratorRequest request) throws GeneratorException {
    ImmutableList<ServiceContext> services = buildServiceContexts(request);
    if (services.isEmpty()) {
      return Stream.empty();
    }

    PluginProtos.CodeGeneratorResponse.File factoryFile =
        PluginProtos.CodeGeneratorResponse.File.newBuilder()
            .setName(SERVICE_FACTORY_PATH)
            .setContent(generateFactoryFile(services))
            .build();
    return Stream.concat(services.stream().map(this::buildServiceFile), Stream.of(factoryFile));
  }

  @VisibleForTesting ImmutableList<ServiceContext> buildServiceContexts(PluginProtos.CodeGeneratorRequest request) {
    return request
        .getProtoFileList()
        .stream()
        .peek(this::hasPackage)
        .filter(file -> request.getFileToGenerateList().contains(file.getName()))
        .flatMap(this::getFileLocations)
        .filter(this::isProtoService)
        .filter(this::isBleRpcService)
        .map(fileLocation -> buildServiceContext(
            fileLocation.getKey(), fileLocation.getValue(), ProtoTypeMap.of(request.getProtoFileList())))
        .collect(collectingAndThen(toList(), ImmutableList::copyOf));
  }

  private Stream<AbstractMap.SimpleEntry<FileDescriptorProto, Location>> getFileLocations(FileDescriptorProto file) {
    return file.getSourceCodeInfo()
        .getLocationList()
        .stream()
        .map(location -> new AbstractMap.SimpleEntry<>(file, location));
  }

  private ServiceContext buildServiceContext(FileDescriptorProto protoFile, Location fileLocation, ProtoTypeMap typeMap) {
    int serviceNumber = fileLocation.getPath(SERVICE_NUMBER_OF_PATHS - 1);
    ServiceDescriptorProto serviceProto = protoFile.getService(serviceNumber);
    ServiceContext serviceContext = new ServiceContext();
    serviceContext.serviceName = serviceProto.getName();
    serviceContext.className = RX_CLASS_PREFIX + serviceContext.serviceName;
    serviceContext.fileName = serviceContext.className + JAVA_SOURCE_EXTENSION;
    serviceContext.deprecated =
        serviceProto.getOptions() != null && serviceProto.getOptions().getDeprecated();
    serviceContext.methods = protoFile
            .getSourceCodeInfo()
            .getLocationList()
            .stream()
            .filter(location -> isProtoMethod(location, serviceNumber))
            .map(location -> buildMethodContext(serviceProto, location, typeMap))
            .collect(collectingAndThen(toList(), ImmutableList::copyOf));
    serviceContext.javaDoc =
        getJavaDoc(fileLocation.getLeadingComments(), SERVICE_JAVADOC_PREFIX);
    serviceContext.packageName = extractPackageName(protoFile);
    return serviceContext;
  }

  private String extractPackageName(FileDescriptorProto proto) {
    FileOptions options = proto.getOptions();
    if (options != null && !Strings.isNullOrEmpty(options.getJavaPackage())) {
      return options.getJavaPackage();
    }
    return proto.getPackage();
  }

  private MethodContext buildMethodContext(
      ServiceDescriptorProto serviceProto, Location location, ProtoTypeMap typeMap) {
    int methodNumber = location.getPath(METHOD_NUMBER_OF_PATHS - 1);
    MethodDescriptorProto methodProto = serviceProto.getMethod(methodNumber);
    checkArgument(!methodProto.getClientStreaming(), "BleRpc doesn't support client streaming to BLE device.");
    MethodContext methodContext = new MethodContext();
    methodContext.methodName = lowerCaseFirstLetter(methodProto.getName());
    methodContext.inputType = typeMap.toJavaTypeName(methodProto.getInputType());
    methodContext.outputType = typeMap.toJavaTypeName(methodProto.getOutputType());
    methodContext.deprecated =
        methodProto.getOptions() != null && methodProto.getOptions().getDeprecated();
    methodContext.isManyOutput = methodProto.getServerStreaming();
    methodContext.javaDoc = getJavaDoc(location.getLeadingComments(), METHOD_JAVADOC_PREFIX);
    return methodContext;
  }

  private String lowerCaseFirstLetter(String string) {
    return Character.toLowerCase(string.charAt(0)) + string.substring(1);
  }

  private String generateFactoryFile(ImmutableList<ServiceContext> services) {
    FileContext fileContext = new FileContext();
    fileContext.services = services;
    return applyTemplate(FACTORY_TEMPLATE_FILE, fileContext);
  }

  private PluginProtos.CodeGeneratorResponse.File buildServiceFile(ServiceContext context) {
    return PluginProtos.CodeGeneratorResponse.File.newBuilder()
        .setName(fullFileName(context))
        .setContent(applyTemplate(RX_SERVICE_TEMPLATE_FILE, context))
        .build();
  }

  private String fullFileName(ServiceContext context) {
    return Paths.get(context.packageName.replace(".", File.separator), context.fileName).toString();
  }

  private String getJavaDoc(String comments, String prefix) {
    if (comments.isEmpty()) {
      return null;
    }
    StringBuilder builder = new StringBuilder("/**\n").append(prefix).append(" * <pre>\n");
    Arrays.stream(HtmlEscapers.htmlEscaper().escape(comments).split("\n"))
        .forEach(line -> builder.append(prefix).append(" * ").append(line).append("\n"));
    builder.append(prefix).append(" * <pre>\n").append(prefix).append(" */");
    return builder.toString();
  }

  private boolean isProtoService(AbstractMap.SimpleEntry<FileDescriptorProto, Location> fileLocation) {
    return fileLocation.getValue().getPathCount() == SERVICE_NUMBER_OF_PATHS
        && fileLocation.getValue().getPath(0) == FileDescriptorProto.SERVICE_FIELD_NUMBER;
  }

  private boolean isBleRpcService(AbstractMap.SimpleEntry<FileDescriptorProto, Location> fileLocation) {
    return fileLocation
        .getKey()
        .getService(fileLocation.getValue().getPath(SERVICE_NUMBER_OF_PATHS - 1))
        .getOptions()
        .getUnknownFields()
        .hasField(Blerpc.SERVICE_FIELD_NUMBER);
  }

  private boolean isProtoMethod(Location location, int serviceNumber) {
    return location.getPathCount() == METHOD_NUMBER_OF_PATHS
        && location.getPath(0) == FileDescriptorProto.SERVICE_FIELD_NUMBER
        && location.getPath(1) == serviceNumber
        && location.getPath(2) == ServiceDescriptorProto.METHOD_FIELD_NUMBER;
  }

  private void hasPackage(FileDescriptorProto file) {
    checkArgument(!Strings.isNullOrEmpty(file.getPackage()), "Proto file must contains package name.");
  }

  /** Template class that describe protobuf file. */
  @SuppressWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
  private static class FileContext {
    public ImmutableList<ServiceContext> services = ImmutableList.of();
  }

  /** Template class that describe protobuf services. */
  @SuppressWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
  @VisibleForTesting static class ServiceContext {
    public String fileName;
    public String packageName;
    public String className;
    public String serviceName;
    public boolean deprecated;
    @Nullable public String javaDoc;
    public ImmutableList<MethodContext> methods = ImmutableList.of();

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (object == null || getClass() != object.getClass()) {
        return false;
      }
      ServiceContext otherService = (ServiceContext) object;
      return Objects.equals(fileName, otherService.fileName)
        && Objects.equals(packageName, otherService.packageName)
        && Objects.equals(className, otherService.className)
        && Objects.equals(serviceName, otherService.serviceName)
        && Objects.equals(javaDoc, otherService.javaDoc)
        && Objects.equals(methods, otherService.methods)
        && deprecated == otherService.deprecated;
    }
  }

  /** Template class that describe protobuf methods. */
  @SuppressWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
  @VisibleForTesting static class MethodContext {
    public String methodName;
    public String inputType;
    public String outputType;
    public boolean deprecated;
    public boolean isManyOutput;
    @Nullable public String javaDoc;

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (object == null || getClass() != object.getClass()) {
        return false;
      }
      MethodContext otherMethod = (MethodContext) object;
      return Objects.equals(methodName, otherMethod.methodName)
          && Objects.equals(inputType, otherMethod.inputType)
          && Objects.equals(outputType, otherMethod.outputType)
          && Objects.equals(javaDoc, otherMethod.javaDoc)
          && deprecated == otherMethod.deprecated
          && isManyOutput == otherMethod.isManyOutput;
    }
  }
}
