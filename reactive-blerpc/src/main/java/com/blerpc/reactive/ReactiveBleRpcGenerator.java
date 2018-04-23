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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Protoc generator that generate RxJava wrappers for BleRpc services. */
public class ReactiveBleRpcGenerator extends Generator {

  private static final int METHOD_NUMBER_OF_PATHS = 4;
  private static final String SERVICE_JAVADOC_PREFIX = "";
  private static final String METHOD_JAVADOC_PREFIX = "  ";
  private static final String CLASS_PREFIX = "Rx";
  private static final String MUSTACHE_NAME = CLASS_PREFIX + "Stub.mustache";

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
    List<PluginProtos.CodeGeneratorResponse.File> files = generateFiles(services);
    return files.stream();
  }

  private List<ServiceContext> findServices(
      List<FileDescriptorProto> protos, ProtoTypeMap typeMap) {
    List<ServiceContext> contexts = new ArrayList<>();
    protos.forEach(
        fileProto -> {
          List<Location> locations = fileProto.getSourceCodeInfo().getLocationList();
          locations
              .stream()
              .filter(
                  location ->
                      location.getPathCount() == 2
                          && location.getPath(0) == FileDescriptorProto.SERVICE_FIELD_NUMBER)
              .forEach(
                  location -> {
                    int serviceNumber = location.getPath(1);
                    if (!fileProto
                        .getService(serviceNumber)
                        .getOptions()
                        .getUnknownFields()
                        .hasField(Blerpc.SERVICE_FIELD_NUMBER)) {
                      return;
                    }
                    ServiceContext serviceContext =
                        buildServiceContext(
                            fileProto.getService(serviceNumber), typeMap, locations, serviceNumber);
                    serviceContext.javaDoc =
                        getJavaDoc(getComments(location), SERVICE_JAVADOC_PREFIX);
                    serviceContext.protoName = fileProto.getName();
                    serviceContext.packageName = extractPackageName(fileProto);
                    contexts.add(serviceContext);
                  });
        });

    return contexts;
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
        .filter(
            location ->
                location.getPathCount() == METHOD_NUMBER_OF_PATHS
                    && location.getPath(0) == FileDescriptorProto.SERVICE_FIELD_NUMBER
                    && location.getPath(1) == serviceNumber
                    && location.getPath(2) == ServiceDescriptorProto.METHOD_FIELD_NUMBER)
        .forEach(
            location -> {
              int methodNumber = location.getPath(METHOD_NUMBER_OF_PATHS - 1);
              MethodContext methodContext =
                  buildMethodContext(serviceProto.getMethod(methodNumber), typeMap);
              methodContext.methodNumber = methodNumber;
              methodContext.javaDoc = getJavaDoc(getComments(location), METHOD_JAVADOC_PREFIX);
              serviceContext.methods.add(methodContext);
            });
    return serviceContext;
  }

  private MethodContext buildMethodContext(
      MethodDescriptorProto methodProto, ProtoTypeMap typeMap) {
    MethodContext methodContext = new MethodContext();
    methodContext.methodName = lowerCaseFirst(methodProto.getName());
    methodContext.inputType = typeMap.toJavaTypeName(methodProto.getInputType());
    methodContext.outputType = typeMap.toJavaTypeName(methodProto.getOutputType());
    methodContext.deprecated =
        methodProto.getOptions() != null && methodProto.getOptions().getDeprecated();
    methodContext.isManyInput = methodProto.getClientStreaming();
    methodContext.isManyOutput = methodProto.getServerStreaming();
    return methodContext;
  }

  private String lowerCaseFirst(String s) {
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }

  private List<PluginProtos.CodeGeneratorResponse.File> generateFiles(
      List<ServiceContext> services) {
    return services.stream().map(this::buildFile).collect(Collectors.toList());
  }

  private PluginProtos.CodeGeneratorResponse.File buildFile(ServiceContext context) {
    String content = applyTemplate(MUSTACHE_NAME, context);
    return PluginProtos.CodeGeneratorResponse.File.newBuilder()
        .setName(absoluteFileName(context))
        .setContent(content)
        .build();
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

  /** Template class that describe protobuf services. */
  private class ServiceContext {
    // CHECKSTYLE DISABLE VisibilityModifier FOR 10 LINES
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
  private class MethodContext {
    // CHECKSTYLE DISABLE VisibilityModifier FOR 8 LINES
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
