package com.blerpc.ios;

import static com.google.common.base.Preconditions.checkArgument;

import com.blerpc.proto.Blerpc;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.compiler.PluginProtos;
import com.salesforce.jprotoc.Generator;
import com.salesforce.jprotoc.GeneratorException;
import com.salesforce.jprotoc.ProtoTypeMap;
import com.salesforce.jprotoc.ProtocPlugin;

import edu.umd.cs.findbugs.annotations.SuppressWarnings;

import java.io.File;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.stream.Stream;

/** Protoc generator that generate Swift PromiseKit wrappers for BleRpc services. */
public class SwiftPromiseKitBleRpcGenerator extends Generator {

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
  private static final String OUTPUT_FILE_EXTENSION = ".swift";
  private static final String TEMPLATE_FILE = "SwiftBleRpcService.mustache";

  public static void main(String[] args) {
    ArrayList<Generator> generators = new ArrayList<>();
    generators.add(new SwiftPromiseKitBleRpcGenerator());
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
    Stream<ServiceContext> services = buildServiceContexts(request);
    return services.map(this::buildServiceFile);
  }

  @VisibleForTesting
  Stream<ServiceContext> buildServiceContexts(PluginProtos.CodeGeneratorRequest request) {
    ProtoTypeMap protoTypeMap = ProtoTypeMap.of(request.getProtoFileList());
    return request
        .getProtoFileList()
        .stream()
        .filter(this::hasPackage)
        .filter(file -> request.getFileToGenerateList().contains(file.getName()))
        .flatMap(this::getFileLocations)
        .filter(this::isProtoService)
        .filter(this::isBleRpcService)
        .map(
            fileLocation ->
                buildServiceContext(
                    fileLocation.getKey(),
                    fileLocation.getValue(),
                    protoTypeMap))
        .collect(ImmutableList.toImmutableList()).stream();
  }

  private Stream<AbstractMap.SimpleEntry<FileDescriptorProto, Location>> getFileLocations(
      FileDescriptorProto file) {
    return file.getSourceCodeInfo()
        .getLocationList()
        .stream()
        .map(location -> new AbstractMap.SimpleEntry<>(file, location));
  }

  private ServiceContext buildServiceContext(
      FileDescriptorProto protoFile, Location fileLocation, ProtoTypeMap typeMap) {
    int serviceNumber = fileLocation.getPath(SERVICE_NUMBER_OF_PATHS - 1);
    ServiceDescriptorProto serviceProto = protoFile.getService(serviceNumber);
    ServiceContext serviceContext = new ServiceContext();
    serviceContext.serviceName = serviceProto.getName();
    serviceContext.className = serviceContext.serviceName;
    serviceContext.fileName = serviceContext.className + OUTPUT_FILE_EXTENSION;
    serviceContext.deprecated = serviceProto.getOptions().getDeprecated();
    serviceContext.packageName = extractPackageName(protoFile);
    serviceContext.methods =
        protoFile
            .getSourceCodeInfo()
            .getLocationList()
            .stream()
            .filter(location -> isProtoMethod(location, serviceNumber))
            .map(location -> buildMethodContext(serviceProto, location, typeMap, protoFile))
            .collect(ImmutableList.toImmutableList());

    if (serviceContext.methods.size() > 0) {
      MethodContext methodContext = serviceContext.methods.get(0);
      serviceContext.serviceUUID = methodContext.serviceUUID;
    }

    return serviceContext;
  }

  private MethodContext buildMethodContext(
      ServiceDescriptorProto serviceProto, Location location, ProtoTypeMap typeMap, FileDescriptorProto protoFile) {
    int methodNumber = location.getPath(METHOD_NUMBER_OF_PATHS - 1);
    MethodDescriptorProto methodProto = serviceProto.getMethod(methodNumber);
    checkArgument(
        !methodProto.getClientStreaming(),
        "BleRpc doesn't support client streaming to BLE device.");
    MethodContext methodContext = new MethodContext();
    methodContext.methodName = lowerCaseFirstLetter(methodProto.getName());
    methodContext.upperCasedMethodName = upperCaseFirstLetter(methodContext.methodName);
    methodContext.characteristicUUID = methodProto.getOptions().getExtension(Blerpc.characteristic).getUuid();

    String characteristicUUID = methodProto.getOptions().getExtension(Blerpc.characteristic).getUuid();
    String[] splittedCharacteristicUUID = characteristicUUID.split("-");
    String serviceUUID = splittedCharacteristicUUID[0];
    splittedCharacteristicUUID[0] = "";
    serviceUUID = serviceUUID.substring(0,serviceUUID.length() - 1) + '0'
            + String.join("-", splittedCharacteristicUUID);

    methodContext.serviceUUID = serviceUUID;

    com.blerpc.proto.MethodType methodType = methodProto.getOptions().getExtension(Blerpc.characteristic).getType();

    switch (methodType) {
      case READ: {
        methodContext.typeRead = "READ";
        break;
      }
      case WRITE: {
        methodContext.typeWrite = "WRITE";
        break;
      }
      case SUBSCRIBE: {
        methodContext.typeSubscribe = "SUBSCRIBE";
        break;
      }
      default:
        break;
    }

    methodContext.inputType = extractSwiftPackageName(protoFile)
            + methodProto.getInputType().split("\\.")[methodProto.getInputType().split("\\.").length - 1];
    methodContext.outputType = extractSwiftPackageName(protoFile)
            + methodProto.getOutputType().split("\\.")[methodProto.getOutputType().split("\\.").length - 1];
    methodContext.deprecated = methodProto.getOptions().getDeprecated();
    methodContext.isManyOutput = methodProto.getServerStreaming();
    return methodContext;
  }

  private PluginProtos.CodeGeneratorResponse.File buildServiceFile(ServiceContext context) {
    return PluginProtos.CodeGeneratorResponse.File.newBuilder()
        .setName(fullFileName(context))
        .setContent(applyTemplate(TEMPLATE_FILE, context))
        .build();
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

  private String fullFileName(ServiceContext context) {
    return Paths.get(context.packageName.replace(".", File.separator), context.fileName).toString();
  }

  private String lowerCaseFirstLetter(String string) {
    return Character.toLowerCase(string.charAt(0)) + string.substring(1);
  }

  private String upperCaseFirstLetter(String string) {
    return Character.toUpperCase(string.charAt(0)) + string.substring(1);
  }

  private boolean isProtoService(
      AbstractMap.SimpleEntry<FileDescriptorProto, Location> fileLocation) {
    return fileLocation.getValue().getPathCount() == SERVICE_NUMBER_OF_PATHS
        && fileLocation.getValue().getPath(0) == FileDescriptorProto.SERVICE_FIELD_NUMBER;
  }

  private boolean isBleRpcService(
      AbstractMap.SimpleEntry<FileDescriptorProto, Location> fileLocation) {
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

  private boolean hasPackage(FileDescriptorProto file) {
    return !file.getPackage().isEmpty();
  }

  /** Template class that describe protobuf services. */
  @SuppressWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
  @VisibleForTesting
  static class ServiceContext {
    public String fileName;
    public String packageName;
    public String className;
    public String serviceName;
    public String serviceUUID;
    public boolean deprecated;
    public ImmutableList<MethodContext> methods = ImmutableList.of();
  }

  /** Template class that describe protobuf methods. */
  @SuppressWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
  @VisibleForTesting
  static class MethodContext {
    public String methodName;
    public String upperCasedMethodName;
    public String inputType;
    public String characteristicUUID;
    public String serviceUUID;
    public String outputType;
    public String typeWrite;
    public String typeRead;
    public String typeSubscribe;
    public boolean deprecated;
    public boolean isManyOutput;
  }
}
