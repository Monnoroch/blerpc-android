package com.blerpc.ios;

import static com.google.common.base.Preconditions.checkArgument;

import com.blerpc.proto.Blerpc;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.compiler.PluginProtos;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import java.util.AbstractMap;
import java.util.stream.Stream;

class ServiceGenerator {

    private static final int METHOD_NUMBER_OF_PATHS = 4;
    private static final int SERVICE_NUMBER_OF_PATHS = 2;
    private static final String OUTPUT_FILE_EXTENSION = ".swift";

    Stream<ServiceContext> buildServiceContexts(PluginProtos.CodeGeneratorRequest request) {
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
                                        fileLocation.getValue()))
                .collect(ImmutableList.toImmutableList()).stream();
    }

    ServiceContext buildServiceContext(
            DescriptorProtos.FileDescriptorProto protoFile, DescriptorProtos.SourceCodeInfo.Location fileLocation) {
        int serviceNumber = fileLocation.getPath(SERVICE_NUMBER_OF_PATHS - 1);
        DescriptorProtos.ServiceDescriptorProto serviceProto = protoFile.getService(serviceNumber);
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
                        .map(location -> buildMethodContext(serviceProto, location, protoFile))
                        .collect(ImmutableList.toImmutableList());

        if (serviceContext.methods.size() > 0) {
            MethodContext methodContext = serviceContext.methods.get(0);
            serviceContext.serviceUUID = methodContext.serviceUUID;
        }

        return serviceContext;
    }

    private MethodContext buildMethodContext(DescriptorProtos.ServiceDescriptorProto serviceProto,
                                             DescriptorProtos.SourceCodeInfo.Location location,
                                             DescriptorProtos.FileDescriptorProto protoFile) {
        int methodNumber = location.getPath(METHOD_NUMBER_OF_PATHS - 1);
        DescriptorProtos.MethodDescriptorProto methodProto = serviceProto.getMethod(methodNumber);
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

    private Stream<AbstractMap.SimpleEntry<DescriptorProtos.FileDescriptorProto, DescriptorProtos.SourceCodeInfo.Location>> getFileLocations(
            DescriptorProtos.FileDescriptorProto file) {
        return file.getSourceCodeInfo()
                .getLocationList()
                .stream()
                .map(location -> new AbstractMap.SimpleEntry<>(file, location));
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
        StringBuilder swiftPackageName = new StringBuilder();

        for (String substring : splittedPackageName) {
            swiftPackageName.append(upperCaseFirstLetter(substring)).append("_");
        }

        return swiftPackageName.toString();
    }

    private String lowerCaseFirstLetter(String string) {
        return Character.toLowerCase(string.charAt(0)) + string.substring(1);
    }

    private String upperCaseFirstLetter(String string) {
        return Character.toUpperCase(string.charAt(0)) + string.substring(1);
    }

    private boolean isProtoService(
            AbstractMap.SimpleEntry<DescriptorProtos.FileDescriptorProto, DescriptorProtos.SourceCodeInfo.Location> fileLocation) {
        return fileLocation.getValue().getPathCount() == SERVICE_NUMBER_OF_PATHS
                && fileLocation.getValue().getPath(0) == DescriptorProtos.FileDescriptorProto.SERVICE_FIELD_NUMBER;
    }

    private boolean isBleRpcService(
            AbstractMap.SimpleEntry<DescriptorProtos.FileDescriptorProto, DescriptorProtos.SourceCodeInfo.Location> fileLocation) {
        return fileLocation
                .getKey()
                .getService(fileLocation.getValue().getPath(SERVICE_NUMBER_OF_PATHS - 1))
                .getOptions()
                .getUnknownFields()
                .hasField(Blerpc.SERVICE_FIELD_NUMBER);
    }

    private boolean isProtoMethod(DescriptorProtos.SourceCodeInfo.Location location, int serviceNumber) {
        return location.getPathCount() == METHOD_NUMBER_OF_PATHS
                && location.getPath(0) == DescriptorProtos.FileDescriptorProto.SERVICE_FIELD_NUMBER
                && location.getPath(1) == serviceNumber
                && location.getPath(2) == DescriptorProtos.ServiceDescriptorProto.METHOD_FIELD_NUMBER;
    }

    /** Template class that describe protobuf services. */
    @SuppressWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    @VisibleForTesting
    public static class ServiceContext {
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
    public static class MethodContext {
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
