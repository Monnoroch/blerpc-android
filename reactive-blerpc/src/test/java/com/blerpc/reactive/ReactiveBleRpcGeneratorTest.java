package com.blerpc.reactive;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.blerpc.proto.Blerpc;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.extensions.proto.ProtoTruth;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.UnknownFieldSet;
import com.google.protobuf.compiler.PluginProtos;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ReactiveBleRpcGenerator}. */
@RunWith(JUnit4.class)
public class ReactiveBleRpcGeneratorTest {

  static final String FILE_NAME = "test_service";
  static final String SERVICE_NAME = "TestService";
  static final String PROTO_PACKAGE = "com.test";
  static final String JAVA_PACKAGE = "com.test.proto";
  static final String SERVICE_JAVADOC = "Service javadoc.";
  static final String READ_METHOD_JAVADOC = "Read method javadoc.";
  static final String SUBSCRIBE_METHOD_JAVADOC = "Subscribe method javadoc.";
  static final String SERVICE_JAVADOC_TEMPLATE = "/**%n * <pre>%n * %s%n * <pre>%n */";
  static final String METHOD_JAVADOC_TEMPLATE = "/**%n   * <pre>%n   * %s%n   * <pre>%n   */";
  static final String READ_METHOD_NAME = "ReadValue";
  static final String READ_METHOD_NAME_FIRST_LOWER_CASE = "readValue";
  static final String SUBSCRIBE_METHOD_NAME = "SubscribeValue";
  static final String SUBSCRIBE_METHOD_NAME_FIRST_LOWER_CASE = "subscribeValue";
  static final String METHOD_INPUT_TYPE = "TestInputValue";
  static final String METHOD_INPUT_TYPE_FULL_PATH = ".com.test.TestInputValue";
  static final String METHOD_INPUT_TYPE_JAVA_PATH = "com.test.proto.TestInputValue";
  static final String METHOD_INPUT_TYPE_PROTO_PATH = "com.test.TestInputValue";
  static final String METHOD_OUTPUT_TYPE = "TestOutputValue";
  static final String METHOD_OUTPUT_TYPE_FULL_PATH = ".com.test.TestOutputValue";
  static final String METHOD_OUTPUT_TYPE_JAVA_PATH = "com.test.proto.TestOutputValue";
  static final String METHOD_OUTPUT_TYPE_PROTO_PATH = "com.test.TestOutputValue";
  static final String RX_CLASS_PREFIX = "Rx";
  static final String SERVICE_FILE_NAME = "RxTestService.java";
  static final String SERVICE_FULL_PATH = "com/test/proto/RxTestService.java";
  static final String FACTORY_FULL_PATH = "com/blerpc/reactive/BleServiceFactory.java";
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
  static final DescriptorProtos.SourceCodeInfo.Location READ_METHOD_LOCATION =
      DescriptorProtos.SourceCodeInfo.Location.newBuilder()
          .addAllPath(ImmutableList.of(6, 0, 2, 0))
          .setLeadingComments(READ_METHOD_JAVADOC)
          .build();
  static final DescriptorProtos.SourceCodeInfo.Location SUBSCRIBE_METHOD_LOCATION =
      DescriptorProtos.SourceCodeInfo.Location.newBuilder()
          .addAllPath(ImmutableList.of(6, 0, 2, 1))
          .setLeadingComments(SUBSCRIBE_METHOD_JAVADOC)
          .build();
  static final DescriptorProtos.SourceCodeInfo FILE_SOURCE_CODE_INFO =
      DescriptorProtos.SourceCodeInfo.newBuilder()
          .addLocation(SERVICE_LOCATION)
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

  ReactiveBleRpcGenerator generator;

  /** Set up. */
  @Before
  public void setUp() {
    generator = new ReactiveBleRpcGenerator();
  }

  @Test
  public void generate() throws Exception {
    Stream<PluginProtos.CodeGeneratorResponse.File> generatedFiles = generator.generate(REQUEST);
    ProtoTruth.assertThat(generatedFiles.collect(ImmutableList.toImmutableList()))
        .ignoringFields(PluginProtos.CodeGeneratorResponse.File.CONTENT_FIELD_NUMBER)
        .containsExactlyElementsIn(
            ImmutableList.of(
                PluginProtos.CodeGeneratorResponse.File.newBuilder()
                    .setName(SERVICE_FULL_PATH)
                    .build(),
                PluginProtos.CodeGeneratorResponse.File.newBuilder()
                    .setName(FACTORY_FULL_PATH)
                    .build()));
  }

  @Test
  public void buildServiceContexts() throws Exception {
    assertEquals(generator.buildServiceContexts(REQUEST), createServiceContext());
  }

  @Test
  public void buildServiceContexts_serviceDeprecated() throws Exception {
    PluginProtos.CodeGeneratorRequest request =
        PluginProtos.CodeGeneratorRequest.newBuilder()
            .addFileToGenerate(FILE_NAME)
            .addProtoFile(
                FILE.toBuilder()
                    .setService(
                        0,
                        SERVICE
                            .toBuilder()
                            .setOptions(SERVICE_OPTIONS.toBuilder().setDeprecated(true))))
            .build();
    ReactiveBleRpcGenerator.ServiceContext serviceContext = createServiceContext();
    serviceContext.deprecated = true;
    assertEquals(generator.buildServiceContexts(request), serviceContext);
  }

  @Test
  public void buildServiceContexts_methodDeprecated() throws Exception {
    PluginProtos.CodeGeneratorRequest request =
        PluginProtos.CodeGeneratorRequest.newBuilder()
            .addFileToGenerate(FILE_NAME)
            .addProtoFile(
                FILE.toBuilder()
                    .setService(
                        0,
                        SERVICE
                            .toBuilder()
                            .setMethod(
                                0,
                                READ_METHOD
                                    .toBuilder()
                                    .setOptions(
                                        DescriptorProtos.MethodOptions.newBuilder()
                                            .setDeprecated(true)))
                            .setMethod(
                                1,
                                SUBSCRIBE_METHOD
                                    .toBuilder()
                                    .setOptions(
                                        DescriptorProtos.MethodOptions.newBuilder()
                                            .setDeprecated(true)))))
            .build();
    ReactiveBleRpcGenerator.ServiceContext serviceContext = createServiceContext();
    serviceContext.methods.forEach(methodContext -> methodContext.deprecated = true);
    assertEquals(generator.buildServiceContexts(request), serviceContext);
  }

  @Test
  public void buildServiceContexts_serviceWithoutJavaDoc() throws Exception {
    PluginProtos.CodeGeneratorRequest request =
        PluginProtos.CodeGeneratorRequest.newBuilder()
            .addFileToGenerate(FILE_NAME)
            .addProtoFile(
                FILE.toBuilder()
                    .setSourceCodeInfo(
                        FILE_SOURCE_CODE_INFO
                            .toBuilder()
                            .setLocation(0, SERVICE_LOCATION.toBuilder().clearLeadingComments())))
            .build();
    ReactiveBleRpcGenerator.ServiceContext serviceContext = createServiceContext();
    serviceContext.javaDoc = null;
    assertEquals(generator.buildServiceContexts(request), serviceContext);
  }

  @Test
  public void buildServiceContexts_methodWithoutJavaDoc() throws Exception {
    PluginProtos.CodeGeneratorRequest request =
        PluginProtos.CodeGeneratorRequest.newBuilder()
            .addFileToGenerate(FILE_NAME)
            .addProtoFile(
                FILE.toBuilder()
                    .setSourceCodeInfo(
                        FILE_SOURCE_CODE_INFO
                            .toBuilder()
                            .setLocation(1, READ_METHOD_LOCATION.toBuilder().clearLeadingComments())
                            .setLocation(
                                2, SUBSCRIBE_METHOD_LOCATION.toBuilder().clearLeadingComments())))
            .build();
    ReactiveBleRpcGenerator.ServiceContext serviceContext = createServiceContext();
    serviceContext.methods.forEach(methodContext -> methodContext.javaDoc = null);
    assertEquals(generator.buildServiceContexts(request), serviceContext);
  }

  @Test
  public void buildServiceContexts_noJavaPackage() throws Exception {
    PluginProtos.CodeGeneratorRequest request =
        PluginProtos.CodeGeneratorRequest.newBuilder()
            .addFileToGenerate(FILE_NAME)
            .addProtoFile(FILE.toBuilder().setOptions(FILE_OPTIONS.toBuilder().clearJavaPackage()))
            .build();
    ReactiveBleRpcGenerator.ServiceContext serviceContext = createServiceContext();
    serviceContext.methods.forEach(
        methodContext -> {
          methodContext.inputType = METHOD_INPUT_TYPE_PROTO_PATH;
          methodContext.outputType = METHOD_OUTPUT_TYPE_PROTO_PATH;
        });
    serviceContext.packageName = PROTO_PACKAGE;
    assertEquals(generator.buildServiceContexts(request), serviceContext);
  }

  @Test
  public void buildServiceContexts_noFileToGenerate() throws Exception {
    PluginProtos.CodeGeneratorRequest request = REQUEST.toBuilder().clearFileToGenerate().build();
    assertThat(generator.buildServiceContexts(request)).isEmpty();
  }

  @Test
  public void buildServiceContexts_noProtoPackage() throws Exception {
    PluginProtos.CodeGeneratorRequest request =
        PluginProtos.CodeGeneratorRequest.newBuilder()
            .addFileToGenerate(FILE_NAME)
            .addProtoFile(FILE.toBuilder().clearPackage())
            .build();
    assertThat(generator.buildServiceContexts(request)).isEmpty();
  }

  @Test
  public void buildServiceContexts_notBleRpcService() throws Exception {
    PluginProtos.CodeGeneratorRequest request =
        PluginProtos.CodeGeneratorRequest.newBuilder()
            .addFileToGenerate(FILE_NAME)
            .addProtoFile(FILE.toBuilder().setService(0, SERVICE.toBuilder().clearOptions()))
            .build();
    assertThat(generator.buildServiceContexts(request)).isEmpty();
  }

  @Test
  public void buildServiceContexts_filterNotServiceLocation_pathCount() throws Exception {
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
    assertThat(generator.buildServiceContexts(request)).isEmpty();
  }

  @Test
  public void buildServiceContexts_filterNotServiceLocation_elementType() throws Exception {
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
    assertThat(generator.buildServiceContexts(request)).isEmpty();
  }

  @Test
  public void buildServiceContexts_filterNotMethodLocation_pathCount() throws Exception {
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
    ReactiveBleRpcGenerator.ServiceContext serviceContext = createServiceContext();
    serviceContext.methods = ImmutableList.of(createSubscribeMethodContext());
    assertEquals(generator.buildServiceContexts(request), serviceContext);
  }

  @Test
  public void buildServiceContexts_filterNotMethodLocation_serviceElementType() throws Exception {
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
    ReactiveBleRpcGenerator.ServiceContext serviceContext = createServiceContext();
    serviceContext.methods = ImmutableList.of(createSubscribeMethodContext());
    assertEquals(generator.buildServiceContexts(request), serviceContext);
  }

  @Test
  public void buildServiceContexts_filterNotMethodLocation_methodElementType_serviceIndex()
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
    ReactiveBleRpcGenerator.ServiceContext serviceContext = createServiceContext();
    serviceContext.methods = ImmutableList.of(createSubscribeMethodContext());
    assertEquals(generator.buildServiceContexts(request), serviceContext);
  }

  @Test
  public void buildServiceContexts_filterNotMethodLocation_methodElementType() throws Exception {
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
    ReactiveBleRpcGenerator.ServiceContext serviceContext = createServiceContext();
    serviceContext.methods = ImmutableList.of(createSubscribeMethodContext());
    assertEquals(generator.buildServiceContexts(request), serviceContext);
  }

  @Test
  public void buildServiceContexts_clientStreamingInput() throws Exception {
    PluginProtos.CodeGeneratorRequest request =
        PluginProtos.CodeGeneratorRequest.newBuilder()
            .addFileToGenerate(FILE_NAME)
            .addProtoFile(
                FILE.toBuilder()
                    .setService(
                        0,
                        SERVICE
                            .toBuilder()
                            .setMethod(1, SUBSCRIBE_METHOD.toBuilder().setClientStreaming(true))))
            .build();
    Exception exception =
        assertThrows(IllegalArgumentException.class, () -> generator.generate(request));
    assertThat(exception.getMessage())
        .contains("BleRpc doesn't support client streaming to BLE device.");
  }

  private ReactiveBleRpcGenerator.ServiceContext createServiceContext() {
    ReactiveBleRpcGenerator.ServiceContext serviceContext =
        new ReactiveBleRpcGenerator.ServiceContext();
    serviceContext.serviceName = SERVICE_NAME;
    serviceContext.className = RX_CLASS_PREFIX + SERVICE_NAME;
    serviceContext.fileName = SERVICE_FILE_NAME;
    serviceContext.javaDoc = String.format(SERVICE_JAVADOC_TEMPLATE, SERVICE_JAVADOC);
    serviceContext.packageName = JAVA_PACKAGE;
    serviceContext.methods =
        ImmutableList.of(createReadMethodContext(), createSubscribeMethodContext());
    return serviceContext;
  }

  private ReactiveBleRpcGenerator.MethodContext createReadMethodContext() {
    ReactiveBleRpcGenerator.MethodContext readMethodContext =
        new ReactiveBleRpcGenerator.MethodContext();
    readMethodContext.methodName = READ_METHOD_NAME_FIRST_LOWER_CASE;
    readMethodContext.inputType = METHOD_INPUT_TYPE_JAVA_PATH;
    readMethodContext.outputType = METHOD_OUTPUT_TYPE_JAVA_PATH;
    readMethodContext.javaDoc = String.format(METHOD_JAVADOC_TEMPLATE, READ_METHOD_JAVADOC);
    return readMethodContext;
  }

  private ReactiveBleRpcGenerator.MethodContext createSubscribeMethodContext() {
    ReactiveBleRpcGenerator.MethodContext subscribeMethodContext =
        new ReactiveBleRpcGenerator.MethodContext();
    subscribeMethodContext.methodName = SUBSCRIBE_METHOD_NAME_FIRST_LOWER_CASE;
    subscribeMethodContext.inputType = METHOD_INPUT_TYPE_JAVA_PATH;
    subscribeMethodContext.outputType = METHOD_OUTPUT_TYPE_JAVA_PATH;
    subscribeMethodContext.javaDoc =
        String.format(METHOD_JAVADOC_TEMPLATE, SUBSCRIBE_METHOD_JAVADOC);
    subscribeMethodContext.isManyOutput = true;
    return subscribeMethodContext;
  }

  private void assertEquals(
      ImmutableList<ReactiveBleRpcGenerator.ServiceContext> servicesList,
      ReactiveBleRpcGenerator.ServiceContext secondService) {
    assertThat(servicesList).hasSize(1);
    assertEquals(servicesList.get(0), secondService);
  }

  private void assertEquals(
      ReactiveBleRpcGenerator.ServiceContext firstService,
      ReactiveBleRpcGenerator.ServiceContext secondService) {
    assertThat(firstService.className).isEqualTo(secondService.className);
    assertThat(firstService.packageName).isEqualTo(secondService.packageName);
    assertThat(firstService.fileName).isEqualTo(secondService.fileName);
    assertThat(firstService.serviceName).isEqualTo(secondService.serviceName);
    assertThat(firstService.javaDoc).isEqualTo(secondService.javaDoc);
    assertThat(firstService.deprecated).isEqualTo(secondService.deprecated);
    assertThat(firstService.methods.size()).isEqualTo(secondService.methods.size());
    for (int i = 0; i < firstService.methods.size(); i++) {
      assertEquals(firstService.methods.get(i), secondService.methods.get(i));
    }
  }

  private void assertEquals(
      ReactiveBleRpcGenerator.MethodContext firstMethod,
      ReactiveBleRpcGenerator.MethodContext secondMethod) {
    assertThat(firstMethod.outputType).isEqualTo(secondMethod.outputType);
    assertThat(firstMethod.inputType).isEqualTo(secondMethod.inputType);
    assertThat(firstMethod.methodName).isEqualTo(secondMethod.methodName);
    assertThat(firstMethod.javaDoc).isEqualTo(secondMethod.javaDoc);
    assertThat(firstMethod.isManyOutput).isEqualTo(secondMethod.isManyOutput);
    assertThat(firstMethod.deprecated).isEqualTo(secondMethod.deprecated);
  }
}
