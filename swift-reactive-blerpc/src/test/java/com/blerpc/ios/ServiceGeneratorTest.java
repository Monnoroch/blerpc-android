package com.blerpc.ios;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.blerpc.proto.Blerpc;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.UnknownFieldSet;
import com.google.protobuf.compiler.PluginProtos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ServiceGenerator}. */
@RunWith(JUnit4.class)
public class ServiceGeneratorTest {
    static final String FILE_NAME = "test_service";
    static final String SERVICE_NAME = "TestService";
    static final String PROTO_PACKAGE = "com.test";
    static final String JAVA_PACKAGE = "com.test.proto";
    static final String SERVICE_JAVADOC = "Service javadoc.";
    static final String READ_METHOD_JAVADOC = "Read method javadoc.";
    static final String SUBSCRIBE_METHOD_JAVADOC = "Subscribe method javadoc.";
    static final String READ_METHOD_NAME = "ReadValue";
    static final String WRITE_METHOD_NAME = "WriteValue";
    static final String SUBSCRIBE_METHOD_NAME = "SubscribeValue";
    static final String METHOD_INPUT_TYPE = "TestInputValue";
    static final String METHOD_INPUT_TYPE_FULL_PATH = ".com.test.TestInputValue";
    static final String METHOD_OUTPUT_TYPE = "TestOutputValue";
    static final String METHOD_OUTPUT_TYPE_FULL_PATH = ".com.test.TestOutputValue";
    static final String READ_METHOD_NAME_FIRST_LOWER_CASE = "readValue";
    static final String WRITE_METHOD_NAME_FIRST_LOWER_CASE = "writeValue";
    static final String SUBSCRIBE_METHOD_NAME_FIRST_LOWER_CASE = "subscribeValue";
    static final String METHOD_INPUT_TYPE_JAVA_PATH = "Com_Test_TestInputValue";
    static final String METHOD_OUTPUT_TYPE_JAVA_PATH = "Com_Test_TestOutputValue";
    static final String SERVICE_FILE_NAME = "TestService.swift";

    static final DescriptorProtos.DescriptorProto INPUT_MESSAGE_TYPE =
            DescriptorProtos.DescriptorProto.newBuilder().setName(METHOD_INPUT_TYPE).build();
    static final DescriptorProtos.DescriptorProto OUTPUT_MESSAGE_TYPE =
            DescriptorProtos.DescriptorProto.newBuilder().setName(METHOD_OUTPUT_TYPE).build();
    static final DescriptorProtos.FileOptions FILE_OPTIONS =
            DescriptorProtos.FileOptions.newBuilder()
                    .setJavaPackage(JAVA_PACKAGE)
                    .setJavaMultipleFiles(true)
                    .build();
    static final DescriptorProtos.SourceCodeInfo.Location SERVICE_LOCATION =
            DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                    .addAllPath(ImmutableList.of(6, 0))
                    .setLeadingComments(SERVICE_JAVADOC)
                    .build();
    static final DescriptorProtos.SourceCodeInfo.Location WRITE_METHOD_LOCATION =
            DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                    .addAllPath(ImmutableList.of(6, 0, 2, 0))
                    .setLeadingComments(READ_METHOD_JAVADOC)
                    .build();
    static final DescriptorProtos.SourceCodeInfo.Location READ_METHOD_LOCATION =
            DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                    .addAllPath(ImmutableList.of(6, 0, 2, 1))
                    .setLeadingComments(READ_METHOD_JAVADOC)
                    .build();
    static final DescriptorProtos.SourceCodeInfo.Location SUBSCRIBE_METHOD_LOCATION =
            DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                    .addAllPath(ImmutableList.of(6, 0, 2, 2))
                    .setLeadingComments(SUBSCRIBE_METHOD_JAVADOC)
                    .build();
    static final DescriptorProtos.SourceCodeInfo FILE_SOURCE_CODE_INFO =
            DescriptorProtos.SourceCodeInfo.newBuilder()
                    .addLocation(SERVICE_LOCATION)
                    .addLocation(WRITE_METHOD_LOCATION)
                    .addLocation(READ_METHOD_LOCATION)
                    .addLocation(SUBSCRIBE_METHOD_LOCATION)
                    .build();
    static final DescriptorProtos.ServiceOptions SERVICE_OPTIONS =
            DescriptorProtos.ServiceOptions.newBuilder()
                    .setUnknownFields(
                            UnknownFieldSet.newBuilder()
                                    .addField(Blerpc.SERVICE_FIELD_NUMBER, UnknownFieldSet.Field.getDefaultInstance())
                                    .build())
                    .build();
    static final DescriptorProtos.MethodDescriptorProto WRITE_METHOD =
            DescriptorProtos.MethodDescriptorProto.newBuilder()
                    .setName(WRITE_METHOD_NAME)
                    .setInputType(METHOD_INPUT_TYPE_FULL_PATH)
                    .setOutputType(METHOD_OUTPUT_TYPE_FULL_PATH)
                    .setServerStreaming(false)
                    .setClientStreaming(false)
                    .build();
    static final DescriptorProtos.MethodDescriptorProto READ_METHOD =
            DescriptorProtos.MethodDescriptorProto.newBuilder()
                    .setName(READ_METHOD_NAME)
                    .setInputType(METHOD_INPUT_TYPE_FULL_PATH)
                    .setOutputType(METHOD_OUTPUT_TYPE_FULL_PATH)
                    .setServerStreaming(false)
                    .setClientStreaming(false)
                    .build();
    static final DescriptorProtos.MethodDescriptorProto SUBSCRIBE_METHOD =
            DescriptorProtos.MethodDescriptorProto.newBuilder()
                    .setName(SUBSCRIBE_METHOD_NAME)
                    .setInputType(METHOD_INPUT_TYPE_FULL_PATH)
                    .setOutputType(METHOD_OUTPUT_TYPE_FULL_PATH)
                    .setServerStreaming(true)
                    .setClientStreaming(false)
                    .build();
    static final DescriptorProtos.ServiceDescriptorProto SERVICE =
            DescriptorProtos.ServiceDescriptorProto.newBuilder()
                    .setOptions(SERVICE_OPTIONS)
                    .setName(SERVICE_NAME)
                    .addMethod(WRITE_METHOD)
                    .addMethod(READ_METHOD)
                    .addMethod(SUBSCRIBE_METHOD)
                    .build();
    static final DescriptorProtos.FileDescriptorProto FILE =
            DescriptorProtos.FileDescriptorProto.newBuilder()
                    .setName(FILE_NAME)
                    .setPackage(PROTO_PACKAGE)
                    .setOptions(FILE_OPTIONS)
                    .setSourceCodeInfo(FILE_SOURCE_CODE_INFO)
                    .addService(SERVICE)
                    .addMessageType(INPUT_MESSAGE_TYPE)
                    .addMessageType(OUTPUT_MESSAGE_TYPE)
                    .build();
    static final PluginProtos.CodeGeneratorRequest REQUEST =
            PluginProtos.CodeGeneratorRequest.newBuilder()
                    .addFileToGenerate(FILE_NAME)
                    .addProtoFile(FILE)
                    .build();

    ServiceGenerator generator;

    /** Set up. */
    @Before
    public void setUp() {
        generator = new ServiceGenerator();
    }

    @Test
    public void buildServiceContexts() throws Exception {
        ImmutableList<ServiceGenerator.ServiceContext> messageList = generator.buildServiceContexts(REQUEST)
                .collect(toImmutableList());
        assertEquals(messageList, createServiceContext());
    }

    @Test
    public void buildServiceContexts_noFileToGenerate() throws Exception {
        PluginProtos.CodeGeneratorRequest request = REQUEST.toBuilder().clearFileToGenerate().build();
        ImmutableList<ServiceGenerator.ServiceContext> serviceList = generator.buildServiceContexts(request)
                .collect(toImmutableList());
        assertThat(serviceList).isEmpty();
    }

    @Test
    public void buildServiceContexts_notBleRpcService() throws Exception {
        PluginProtos.CodeGeneratorRequest request =
                PluginProtos.CodeGeneratorRequest.newBuilder()
                        .addFileToGenerate(FILE_NAME)
                        .addProtoFile(FILE.toBuilder().setService(0, SERVICE.toBuilder().clearOptions()))
                        .build();
        ImmutableList<ServiceGenerator.ServiceContext> serviceList = generator.buildServiceContexts(request)
                .collect(toImmutableList());
        assertThat(serviceList).isEmpty();
    }

    @Test
    public void buildServiceContexts_notServiceLocation_wrongLength() throws Exception {
        PluginProtos.CodeGeneratorRequest request =
                PluginProtos.CodeGeneratorRequest.newBuilder()
                        .addFileToGenerate(FILE_NAME)
                        .addProtoFile(
                                FILE.toBuilder()
                                        .setSourceCodeInfo(
                                                DescriptorProtos.SourceCodeInfo.newBuilder()
                                                        .addLocation(
                                                                DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                                                                        .addAllPath(ImmutableList.of(6, 0, 0)))))
                        .build();
        ImmutableList<ServiceGenerator.ServiceContext> serviceList = generator.buildServiceContexts(request)
                .collect(toImmutableList());
        assertThat(serviceList).isEmpty();
    }

    @Test
    public void buildServiceContexts_notServiceLocation_notServiceTypeNumber() throws Exception {
        PluginProtos.CodeGeneratorRequest request =
                PluginProtos.CodeGeneratorRequest.newBuilder()
                        .addFileToGenerate(FILE_NAME)
                        .addProtoFile(
                                FILE.toBuilder()
                                        .setSourceCodeInfo(
                                                DescriptorProtos.SourceCodeInfo.newBuilder()
                                                        .addLocation(
                                                                DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                                                                        .addAllPath(ImmutableList.of(5, 0)))))
                        .build();
        ImmutableList<ServiceGenerator.ServiceContext> serviceList = generator.buildServiceContexts(request)
                .collect(toImmutableList());
        assertThat(serviceList).isEmpty();
    }

    @Test
    public void buildServiceContexts__notMethodLocation_wrongLength() throws Exception {
        PluginProtos.CodeGeneratorRequest request =
                PluginProtos.CodeGeneratorRequest.newBuilder()
                        .addFileToGenerate(FILE_NAME)
                        .addProtoFile(
                                FILE.toBuilder()
                                        .setSourceCodeInfo(
                                                DescriptorProtos.SourceCodeInfo.newBuilder()
                                                        .addLocation(SERVICE_LOCATION)
                                                        .addLocation(
                                                                DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                                                                        .addAllPath(ImmutableList.of(6, 0, 2, 0, 0)))
                                                        .addLocation(SUBSCRIBE_METHOD_LOCATION)))
                        .build();
        ServiceGenerator.ServiceContext serviceContext = createServiceContext();
        serviceContext.methods = ImmutableList.of(createSubscribeMethodContext());
        ImmutableList<ServiceGenerator.ServiceContext> serviceList = generator.buildServiceContexts(request)
                .collect(toImmutableList());
        assertEquals(serviceList, serviceContext);
    }

    @Test
    public void buildServiceContexts_notMethodLocation_notServiceTypeNumber() throws Exception {
        PluginProtos.CodeGeneratorRequest request =
                PluginProtos.CodeGeneratorRequest.newBuilder()
                        .addFileToGenerate(FILE_NAME)
                        .addProtoFile(
                                FILE.toBuilder()
                                        .setSourceCodeInfo(
                                                DescriptorProtos.SourceCodeInfo.newBuilder()
                                                        .addLocation(SERVICE_LOCATION)
                                                        .addLocation(
                                                                DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                                                                        .addAllPath(ImmutableList.of(5, 0, 2, 0)))
                                                        .addLocation(SUBSCRIBE_METHOD_LOCATION)))
                        .build();
        ImmutableList<ServiceGenerator.ServiceContext> serviceList = generator.buildServiceContexts(request)
                .collect(toImmutableList());
        ServiceGenerator.ServiceContext serviceContext = createServiceContext();
        serviceContext.methods = ImmutableList.of(createSubscribeMethodContext());
        assertEquals(serviceList, serviceContext);
    }

    @Test
    public void buildServiceContexts_notMethodLocation_wrongServiceIndex()
            throws Exception {
        PluginProtos.CodeGeneratorRequest request =
                PluginProtos.CodeGeneratorRequest.newBuilder()
                        .addFileToGenerate(FILE_NAME)
                        .addProtoFile(
                                FILE.toBuilder()
                                        .setSourceCodeInfo(
                                                DescriptorProtos.SourceCodeInfo.newBuilder()
                                                        .addLocation(SERVICE_LOCATION)
                                                        .addLocation(
                                                                DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                                                                        .addAllPath(ImmutableList.of(6, 1, 2, 0)))
                                                        .addLocation(SUBSCRIBE_METHOD_LOCATION)))
                        .build();
        ImmutableList<ServiceGenerator.ServiceContext> serviceList = generator.buildServiceContexts(request)
                .collect(toImmutableList());
        ServiceGenerator.ServiceContext serviceContext = createServiceContext();
        serviceContext.methods = ImmutableList.of(createSubscribeMethodContext());
        assertEquals(serviceList, serviceContext);
    }

    @Test
    public void buildServiceContexts_notMethodLocation_notMethodTypeNumber() throws Exception {
        PluginProtos.CodeGeneratorRequest request =
                PluginProtos.CodeGeneratorRequest.newBuilder()
                        .addFileToGenerate(FILE_NAME)
                        .addProtoFile(
                                FILE.toBuilder()
                                        .setSourceCodeInfo(
                                                DescriptorProtos.SourceCodeInfo.newBuilder()
                                                        .addLocation(SERVICE_LOCATION)
                                                        .addLocation(
                                                                DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                                                                        .addAllPath(ImmutableList.of(6, 0, 3, 0)))
                                                        .addLocation(SUBSCRIBE_METHOD_LOCATION)))
                        .build();
        ImmutableList<ServiceGenerator.ServiceContext> serviceList = generator.buildServiceContexts(request)
                .collect(toImmutableList());
        ServiceGenerator.ServiceContext serviceContext = createServiceContext();
        serviceContext.methods = ImmutableList.of(createSubscribeMethodContext());
        assertEquals(serviceList, serviceContext);
    }

    private ServiceGenerator.ServiceContext createServiceContext() {
        ServiceGenerator.ServiceContext serviceContext =
                new ServiceGenerator.ServiceContext();
        serviceContext.serviceName = SERVICE_NAME;
        serviceContext.className = SERVICE_NAME;
        serviceContext.fileName = SERVICE_FILE_NAME;
        serviceContext.packageName = JAVA_PACKAGE;
        serviceContext.methods =
                ImmutableList.of(createWriteMethodContext(), createReadMethodContext(), createSubscribeMethodContext());
        return serviceContext;
    }

    private ServiceGenerator.MethodContext createWriteMethodContext() {
        ServiceGenerator.MethodContext readMethodContext =
                new ServiceGenerator.MethodContext();
        readMethodContext.methodName = WRITE_METHOD_NAME_FIRST_LOWER_CASE;
        readMethodContext.inputType = METHOD_INPUT_TYPE_JAVA_PATH;
        readMethodContext.outputType = METHOD_OUTPUT_TYPE_JAVA_PATH;
        readMethodContext.upperCasedMethodName = Common.upperCaseFirstLetter(WRITE_METHOD_NAME_FIRST_LOWER_CASE);
        readMethodContext.characteristicUUID = "";
        return readMethodContext;
    }

    private ServiceGenerator.MethodContext createReadMethodContext() {
        ServiceGenerator.MethodContext readMethodContext =
                new ServiceGenerator.MethodContext();
        readMethodContext.methodName = READ_METHOD_NAME_FIRST_LOWER_CASE;
        readMethodContext.inputType = METHOD_INPUT_TYPE_JAVA_PATH;
        readMethodContext.outputType = METHOD_OUTPUT_TYPE_JAVA_PATH;
        readMethodContext.upperCasedMethodName = Common.upperCaseFirstLetter(READ_METHOD_NAME_FIRST_LOWER_CASE);
        readMethodContext.characteristicUUID = "";
        return readMethodContext;
    }

    private ServiceGenerator.MethodContext createSubscribeMethodContext() {
        ServiceGenerator.MethodContext subscribeMethodContext =
                new ServiceGenerator.MethodContext();
        subscribeMethodContext.methodName = SUBSCRIBE_METHOD_NAME_FIRST_LOWER_CASE;
        subscribeMethodContext.inputType = METHOD_INPUT_TYPE_JAVA_PATH;
        subscribeMethodContext.outputType = METHOD_OUTPUT_TYPE_JAVA_PATH;
        subscribeMethodContext.isManyOutput = true;
        return subscribeMethodContext;
    }

    private void assertEquals(
            ImmutableList<ServiceGenerator.ServiceContext> servicesList,
            ServiceGenerator.ServiceContext secondService) {
        assertThat(servicesList).hasSize(1);
        assertEquals(servicesList.get(0), secondService);
    }

    private void assertEquals(
            ServiceGenerator.ServiceContext firstService,
            ServiceGenerator.ServiceContext secondService) {
        assertThat(firstService.className).isEqualTo(secondService.className);
        assertThat(firstService.packageName).isEqualTo(secondService.packageName);
        assertThat(firstService.fileName).isEqualTo(secondService.fileName);
        assertThat(firstService.serviceName).isEqualTo(secondService.serviceName);
        assertThat(firstService.deprecated).isEqualTo(secondService.deprecated);
        assertThat(firstService.methods.size()).isEqualTo(secondService.methods.size());
        for (int i = 0; i < firstService.methods.size(); i++) {
            assertEquals(firstService.methods.get(i), secondService.methods.get(i));
        }
    }

    private void assertEquals(
            ServiceGenerator.MethodContext firstMethod,
            ServiceGenerator.MethodContext secondMethod) {
        assertThat(firstMethod.outputType).isEqualTo(secondMethod.outputType);
        assertThat(firstMethod.inputType).isEqualTo(secondMethod.inputType);
        assertThat(firstMethod.methodName).isEqualTo(secondMethod.methodName);
        assertThat(firstMethod.isManyOutput).isEqualTo(secondMethod.isManyOutput);
        assertThat(firstMethod.deprecated).isEqualTo(secondMethod.deprecated);
    }
}
