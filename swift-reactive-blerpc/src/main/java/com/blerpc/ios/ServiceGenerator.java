package com.blerpc.ios;

import static com.google.common.base.Preconditions.checkArgument;

import com.blerpc.proto.Blerpc;
import com.blerpc.proto.MethodType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Generator which generates services for Rpc methods. */
public class ServiceGenerator {

    private static final int METHOD_NUMBER_OF_PATHS = 4;
    private static final int SERVICE_NUMBER_OF_PATHS = 2;
    private static final String OUTPUT_FILE_EXTENSION = ".swift";
    private static final String SERVICE_UUID_SEPARATOR = "-";
    private static final String SWIFT_PACKAGE_TO_CLASS_SAPARATOR = "_";
    private static final String METHOD_TYPE_READ = "READ";
    private static final String METHOD_TYPE_WRITE = "WRITE";
    private static final String METHOD_TYPE_SUBSCRIBE = "SUBSCRIBE";
    private static final String PROTO_PACKAGE_SEPARATOR = "\\.";

    /**
     * Builds service contexts based on input proto file request.
     * @param request - input request (usually as input proto file).
     * @return prepared service contexts parsed from input proto request.
     */
    public Stream<ServiceContext> buildServiceContexts(CodeGeneratorRequest request) {
        return request
                .getProtoFileList()
                .stream()
                .filter(this::hasPackage)
                .filter(file -> request.getFileToGenerateList().contains(file.getName()))
                .flatMap(this::getFileLocations)
                .filter(this::isProtoService)
                .filter(this::isBleRpcService)
                .map(fileLocation ->
                                buildServiceContext(
                                        fileLocation.getKey(),
                                        fileLocation.getValue()))
                .collect(ImmutableList.toImmutableList()).stream();
    }

    private ServiceContext buildServiceContext(FileDescriptorProto protoFile, Location fileLocation) {
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
                        .map(location -> buildMethodContext(serviceProto, location, protoFile))
                        .collect(ImmutableList.toImmutableList());

        if (serviceContext.methods.size() > 0) {
            MethodContext methodContext = serviceContext.methods.get(0);
            serviceContext.serviceUUID = methodContext.serviceUUID;
        }

        return serviceContext;
    }

    private MethodContext buildMethodContext(ServiceDescriptorProto serviceProto,
                                             Location location,
                                             FileDescriptorProto protoFile) {
        int methodNumber = location.getPath(METHOD_NUMBER_OF_PATHS - 1);
        MethodDescriptorProto methodProto = serviceProto.getMethod(methodNumber);
        checkArgument(
                !methodProto.getClientStreaming(),
                "BleRpc doesn't support client streaming to BLE device.");
        MethodContext methodContext = new MethodContext();
        methodContext.inputType = generateSwiftProtoType(protoFile, methodProto.getInputType());
        methodContext.outputType = generateSwiftProtoType(protoFile, methodProto.getOutputType());

        MethodType methodType = methodProto.getOptions().getExtension(Blerpc.characteristic).getType();

        switch (methodType) {
            case READ: {
                checkIsEmptyRequest(protoFile, methodContext.inputType);
                methodContext.typeRead = METHOD_TYPE_READ;
                break;
            }
            case WRITE: {
                methodContext.typeWrite = METHOD_TYPE_WRITE;
                break;
            }
            case SUBSCRIBE: {
                checkIsEmptyRequest(protoFile, methodContext.inputType);
                methodContext.typeSubscribe = METHOD_TYPE_SUBSCRIBE;
                break;
            }
            default:
                break;
        }

        methodContext.methodName = lowerCaseFirstLetter(methodProto.getName());
        methodContext.upperCasedMethodName = upperCaseFirstLetter(methodContext.methodName);
        methodContext.characteristicUUID = methodProto.getOptions().getExtension(Blerpc.characteristic).getUuid();
        methodContext.serviceUUID = generateServiceUUID(methodProto);
        methodContext.deprecated = methodProto.getOptions().getDeprecated();
        methodContext.isManyOutput = methodProto.getServerStreaming();
        return methodContext;
    }

    private Stream<AbstractMap.SimpleEntry<FileDescriptorProto, Location>> getFileLocations(FileDescriptorProto file) {
        return file.getSourceCodeInfo()
                .getLocationList()
                .stream()
                .map(location -> new AbstractMap.SimpleEntry<>(file, location));
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

    private String lowerCaseFirstLetter(String string) {
        return Character.toLowerCase(string.charAt(0))
                + (string.length() > 1 ? string.substring(1) : "");
    }

    private String upperCaseFirstLetter(String string) {
        return Character.toUpperCase(string.charAt(0))
                + (string.length() > 1 ? string.substring(1) : "");
    }

    private String generateServiceUUID(MethodDescriptorProto methodProto) {
        String characteristicUUID = methodProto.getOptions()
                .getExtension(Blerpc.characteristic).getUuid();
        String[] splittedCharacteristicUUID = characteristicUUID.split(SERVICE_UUID_SEPARATOR);
        String serviceUUID = splittedCharacteristicUUID[0];
        splittedCharacteristicUUID[0] = "";
        return serviceUUID.substring(0, serviceUUID.length() - 1) + '0'
                + String.join(SERVICE_UUID_SEPARATOR, splittedCharacteristicUUID);
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

    private void checkIsEmptyRequest(FileDescriptorProto protoFile, String inputType) {
        protoFile.getMessageTypeList().forEach((message) -> {
            if (generateSwiftProtoType(protoFile, message.getName()).equals(inputType)) {
                checkArgument(
                        message.getFieldCount() == 0,
                        "BleRpc doesn't support non empty requests for read/subscribe methods.");
            }
        });
    }

    private String generateSwiftProtoType(FileDescriptorProto protoFile, String type) {
        return extractSwiftPackageName(protoFile)
                + type.split(PROTO_PACKAGE_SEPARATOR)[type.split(PROTO_PACKAGE_SEPARATOR).length - 1];
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
