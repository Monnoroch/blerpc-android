package com.blerpc.ios;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;

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
import java.util.stream.Stream;

/** Generator which generates services for Rpc methods. */
public class ServiceGenerator {

    private static final int METHOD_NUMBER_OF_PATHS = 4;
    private static final int SERVICE_NUMBER_OF_PATHS = 2;
    private static final String OUTPUT_FILE_EXTENSION = ".swift";
    private static final String METHOD_TYPE_READ = "READ";
    private static final String METHOD_TYPE_WRITE = "WRITE";
    private static final String METHOD_TYPE_SUBSCRIBE = "SUBSCRIBE";

    /**
     * Builds service contexts based on input proto file request.
     * @param request - input request (usually as input proto file).
     * @return prepared service contexts parsed from input proto request.
     */
    public Stream<ServiceContext> buildServiceContexts(CodeGeneratorRequest request) {
        return request
                .getProtoFileList()
                .stream()
                .filter(Common::hasPackage)
                .filter(file -> request.getFileToGenerateList().contains(file.getName()))
                .flatMap(this::getFileLocations)
                .filter(this::isProtoService)
                .filter(this::isBleRpcService)
                .map(fileLocation ->
                                buildServiceContext(
                                        fileLocation.getKey(),
                                        fileLocation.getValue()));
    }

    private ServiceContext buildServiceContext(FileDescriptorProto protoFile, Location fileLocation) {
        int serviceNumber = fileLocation.getPath(SERVICE_NUMBER_OF_PATHS - 1);
        ServiceDescriptorProto serviceProto = protoFile.getService(serviceNumber);
        ServiceContext serviceContext = new ServiceContext();
        serviceContext.serviceName = serviceProto.getName();
        serviceContext.className = serviceContext.serviceName;
        serviceContext.fileName = serviceContext.className + OUTPUT_FILE_EXTENSION;
        serviceContext.deprecated = serviceProto.getOptions().getDeprecated();
        serviceContext.packageName = Common.extractPackageName(protoFile);
        serviceContext.serviceUUID = serviceProto.getOptions()
                .getExtension(Blerpc.service).getUuid();
        serviceContext.methods =
                protoFile
                        .getSourceCodeInfo()
                        .getLocationList()
                        .stream()
                        .filter(location -> isProtoMethod(location, serviceNumber))
                        .map(location -> buildMethodContext(serviceProto, location, protoFile))
                        .collect(toImmutableList());
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
        methodContext.inputType = Common.generateSwiftProtoType(protoFile,
                methodProto.getInputType());
        methodContext.outputType = Common.generateSwiftProtoType(protoFile,
                methodProto.getOutputType());
        MethodType methodType = methodProto.getOptions()
                .getExtension(Blerpc.characteristic).getType();

        switch (methodType) {
            case READ: {
                Common.checkIsEmptyRequest(protoFile, methodContext.inputType);
                methodContext.typeRead = METHOD_TYPE_READ;
                break;
            }
            case WRITE: {
                methodContext.typeWrite = METHOD_TYPE_WRITE;
                break;
            }
            case SUBSCRIBE: {
                Common.checkIsEmptyRequest(protoFile, methodContext.inputType);
                methodContext.typeSubscribe = METHOD_TYPE_SUBSCRIBE;
                break;
            }
            default:
                break;
        }

        methodContext.methodName = Common.lowerCaseFirstLetter(methodProto.getName());
        methodContext.upperCasedMethodName = Common.upperCaseFirstLetter(methodContext.methodName);
        methodContext.characteristicUUID = methodProto.getOptions()
                .getExtension(Blerpc.characteristic).getUuid();
        methodContext.deprecated = methodProto.getOptions().getDeprecated();
        methodContext.isManyOutput = methodProto.getServerStreaming();
        return methodContext;
    }

    private Stream<AbstractMap.SimpleEntry<FileDescriptorProto, Location>> getFileLocations(
            FileDescriptorProto file) {
        return file.getSourceCodeInfo()
                .getLocationList()
                .stream()
                .map(location -> new AbstractMap.SimpleEntry<>(file, location));
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
                .hasExtension(Blerpc.service);
    }

    private boolean isProtoMethod(Location location, int serviceNumber) {
        return location.getPathCount() == METHOD_NUMBER_OF_PATHS
                && location.getPath(0) == FileDescriptorProto.SERVICE_FIELD_NUMBER
                && location.getPath(1) == serviceNumber
                && location.getPath(2) == ServiceDescriptorProto.METHOD_FIELD_NUMBER;
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
        public ImmutableList<MethodContext> methods = ImmutableList.of();
        public boolean deprecated;
    }

    /** Template class that describe protobuf methods. */
    @SuppressWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    @VisibleForTesting
    public static class MethodContext {
        public String methodName;
        public String upperCasedMethodName;
        public String inputType;
        public String characteristicUUID;
        public String outputType;
        public String typeWrite;
        public String typeRead;
        public String typeSubscribe;
        public boolean deprecated;
        public boolean isManyOutput;
    }

}
