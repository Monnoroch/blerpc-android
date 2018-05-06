package com.blerpc.reactive;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.blerpc.proto.Blerpc;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.UnknownFieldSet;
import com.google.protobuf.compiler.PluginProtos;
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
  static final String SERVICE_JAVADOC_TEMPLATE = "/**%n" + " * <pre>%n" + " * %s%n" + " * <pre>%n" + " */";
  static final String METHOD_JAVADOC_TEMPLATE = "/**%n" + "   * <pre>%n" + "   * %s%n" + "   * <pre>%n" + "   */";
  static final String READ_METHOD_NAME = "ReadValue";
  static final String READ_METHOD_NAME_FIRST_LOWER_CASE = "readValue";
  static final String SUBSCRIBE_METHOD_NAME = "SubscribeValue";
  static final String SUBSCRIBE_METHOD_NAME_FIRST_LOWER_CASE = "subscribeValue";
  static final String METHOD_INPUT_TYPE = "TestInputValue";
  static final String RX_CLASS_PREFIX = "Rx";
  static final String JAVA_SOURCE_EXTENSION = ".java";
  static final String METHOD_INPUT_TYPE_FULL_PATH = "." + PROTO_PACKAGE + "." + METHOD_INPUT_TYPE;
  static final String METHOD_OUTPUT_TYPE = "TestOutputValue";
  static final String METHOD_OUTPUT_TYPE_FULL_PATH = "." + PROTO_PACKAGE + "." + METHOD_OUTPUT_TYPE;
  static final DescriptorProtos.DescriptorProto INPUT_MESSAGE_TYPE = DescriptorProtos.DescriptorProto.newBuilder()
      .setName(METHOD_INPUT_TYPE)
      .build();
  static final DescriptorProtos.DescriptorProto OUTPUT_MESSAGE_TYPE = DescriptorProtos.DescriptorProto.newBuilder()
      .setName(METHOD_OUTPUT_TYPE)
      .build();
  static final DescriptorProtos.FileOptions FILE_OPTIONS = DescriptorProtos.FileOptions.newBuilder()
      .setJavaPackage(JAVA_PACKAGE)
      .setJavaMultipleFiles(true)
      .build();
  static final DescriptorProtos.SourceCodeInfo.Location SERVICE_LOCATION = DescriptorProtos.SourceCodeInfo.Location.newBuilder()
      .addAllPath(ImmutableList.of(6, 0))
      .setLeadingComments(SERVICE_JAVADOC)
      .build();
  static final DescriptorProtos.SourceCodeInfo.Location READ_METHOD_LOCATION = DescriptorProtos.SourceCodeInfo.Location.newBuilder()
      .addAllPath(ImmutableList.of(6, 0, 2, 0))
      .setLeadingComments(READ_METHOD_JAVADOC)
      .build();
  static final DescriptorProtos.SourceCodeInfo.Location SUBSCRIBE_METHOD_LOCATION = DescriptorProtos.SourceCodeInfo.Location.newBuilder()
      .addAllPath(ImmutableList.of(6, 0, 2, 1))
      .setLeadingComments(SUBSCRIBE_METHOD_JAVADOC)
      .build();
  static final DescriptorProtos.SourceCodeInfo FILE_SOURCE_CODE_INFO = DescriptorProtos.SourceCodeInfo.newBuilder()
      .addLocation(SERVICE_LOCATION)
      .addLocation(READ_METHOD_LOCATION)
      .addLocation(SUBSCRIBE_METHOD_LOCATION)
      .build();
  static final DescriptorProtos.ServiceOptions SERVICE_OPTIONS = DescriptorProtos.ServiceOptions.newBuilder()
      .setUnknownFields(UnknownFieldSet.newBuilder()
          .addField(Blerpc.SERVICE_FIELD_NUMBER, UnknownFieldSet.Field.getDefaultInstance())
          .build())
      .build();
  static final DescriptorProtos.MethodDescriptorProto READ_METHOD = DescriptorProtos.MethodDescriptorProto.newBuilder()
      .setName(READ_METHOD_NAME)
      .setInputType(METHOD_INPUT_TYPE_FULL_PATH)
      .setOutputType(METHOD_OUTPUT_TYPE_FULL_PATH)
      .setServerStreaming(false)
      .setClientStreaming(false)
      .build();
  static final DescriptorProtos.MethodDescriptorProto SUBSCRIBE_METHOD = DescriptorProtos.MethodDescriptorProto.newBuilder()
      .setName(SUBSCRIBE_METHOD_NAME)
      .setInputType(METHOD_INPUT_TYPE_FULL_PATH)
      .setOutputType(METHOD_OUTPUT_TYPE_FULL_PATH)
      .setServerStreaming(true)
      .setClientStreaming(false)
      .build();
  static final DescriptorProtos.ServiceDescriptorProto SERVICE = DescriptorProtos.ServiceDescriptorProto.newBuilder()
      .setOptions(SERVICE_OPTIONS)
      .setName(SERVICE_NAME)
      .addMethod(READ_METHOD)
      .addMethod(SUBSCRIBE_METHOD)
      .build();
  static final DescriptorProtos.FileDescriptorProto FILE = DescriptorProtos.FileDescriptorProto.newBuilder()
      .setName(FILE_NAME)
      .setPackage(PROTO_PACKAGE)
      .setOptions(FILE_OPTIONS)
      .setSourceCodeInfo(FILE_SOURCE_CODE_INFO)
      .addService(SERVICE)
      .addMessageType(INPUT_MESSAGE_TYPE)
      .addMessageType(OUTPUT_MESSAGE_TYPE)
      .build();
  static final PluginProtos.CodeGeneratorRequest REQUEST = PluginProtos.CodeGeneratorRequest.newBuilder()
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
    assertThat(generator.generate(REQUEST)).hasSize(2);
  }

  @Test
  public void buildServiceContexts() throws Exception {
    assertEquals(generator.buildServiceContexts(REQUEST).get(0), createServiceContext());
  }

  @Test
  public void generate_noFileToGenerate() throws Exception {
    PluginProtos.CodeGeneratorRequest request = REQUEST.toBuilder()
        .clearFileToGenerate()
        .build();
    assertThat(generator.buildServiceContexts(request)).isEmpty();
  }

  @Test
  public void generate_wrongServerNumberOfPath() throws Exception {
    PluginProtos.CodeGeneratorRequest request = PluginProtos.CodeGeneratorRequest.newBuilder()
        .addFileToGenerate(FILE_NAME)
        .addProtoFile(FILE.toBuilder()
            .setSourceCodeInfo(DescriptorProtos.SourceCodeInfo.newBuilder()
                    .addLocation(DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                        .addAllPath(ImmutableList.of(6, 0, 0)))
                    .addLocation(READ_METHOD_LOCATION)
                    .addLocation(SUBSCRIBE_METHOD_LOCATION)))
        .build();
    assertThat(generator.buildServiceContexts(request)).isEmpty();
  }

  @Test
  public void generate_wrongServerFieldNumber() throws Exception {
    PluginProtos.CodeGeneratorRequest request = PluginProtos.CodeGeneratorRequest.newBuilder()
        .addFileToGenerate(FILE_NAME)
        .addProtoFile(FILE.toBuilder()
            .setSourceCodeInfo(DescriptorProtos.SourceCodeInfo.newBuilder()
                .addLocation(DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                    .addAllPath(ImmutableList.of(5, 0)))
                .addLocation(READ_METHOD_LOCATION)
                .addLocation(SUBSCRIBE_METHOD_LOCATION)))
        .build();
    assertThat(generator.buildServiceContexts(request)).isEmpty();
  }

  @Test
  public void generate_notBleRpcService() throws Exception {
    PluginProtos.CodeGeneratorRequest request = PluginProtos.CodeGeneratorRequest.newBuilder()
        .addFileToGenerate(FILE_NAME)
        .addProtoFile(FILE.toBuilder()
            .setService(0, SERVICE.toBuilder()
                .clearOptions()))
        .build();
    assertThat(generator.buildServiceContexts(request)).isEmpty();
  }

  @Test
  public void generate_clientStreamingInput() throws Exception {
    PluginProtos.CodeGeneratorRequest request = PluginProtos.CodeGeneratorRequest.newBuilder()
        .addFileToGenerate(FILE_NAME)
        .addProtoFile(FILE.toBuilder()
            .setService(0, SERVICE.toBuilder()
                .setMethod(1, SUBSCRIBE_METHOD.toBuilder()
                    .setClientStreaming(true))))
        .build();
    Exception exception = assertThrows(IllegalArgumentException.class, () -> generator.generate(request));
    assertThat(exception.getMessage()).contains("BleRpc doesn't support client streaming to BLE device.");
  }

  @Test
  public void generate_noProtoPackage() throws Exception {
    PluginProtos.CodeGeneratorRequest request = PluginProtos.CodeGeneratorRequest.newBuilder()
        .addFileToGenerate(FILE_NAME)
        .addProtoFile(FILE.toBuilder()
            .clearPackage())
        .build();
    Exception exception = assertThrows(IllegalArgumentException.class, () -> generator.generate(request));
    assertThat(exception.getMessage()).contains("Proto file must contains package name.");
  }

  @Test
  public void generate_deprecatedAnnotation() throws Exception {
    ReactiveBleRpcGenerator.ServiceContext serviceContext = createServiceContext();
    serviceContext.deprecated = true;
    serviceContext.methods.forEach(methodContext -> methodContext.deprecated = true);
    PluginProtos.CodeGeneratorRequest request = PluginProtos.CodeGeneratorRequest.newBuilder()
        .addFileToGenerate(FILE_NAME)
        .addProtoFile(FILE.toBuilder()
            .setService(0, SERVICE.toBuilder()
                .setOptions(SERVICE_OPTIONS.toBuilder()
                    .setDeprecated(true))
                .setMethod(0, READ_METHOD.toBuilder()
                    .setOptions(DescriptorProtos.MethodOptions.newBuilder()
                        .setDeprecated(true)))
                .setMethod(1, SUBSCRIBE_METHOD.toBuilder()
                    .setOptions(DescriptorProtos.MethodOptions.newBuilder()
                        .setDeprecated(true)))))
        .build();
    assertEquals(generator.buildServiceContexts(request).get(0), serviceContext);
  }

  @Test
  public void generate_noJavaDoc() throws Exception {
    ReactiveBleRpcGenerator.ServiceContext serviceContext = createServiceContext();
    serviceContext.javaDoc = null;
    serviceContext.methods.forEach(methodContext -> methodContext.javaDoc = null);
    PluginProtos.CodeGeneratorRequest request = PluginProtos.CodeGeneratorRequest.newBuilder()
        .addFileToGenerate(FILE_NAME)
        .addProtoFile(FILE.toBuilder()
            .setSourceCodeInfo(FILE_SOURCE_CODE_INFO.toBuilder()
                .setLocation(0, SERVICE_LOCATION.toBuilder()
                    .clearLeadingComments())
                .setLocation(1, READ_METHOD_LOCATION.toBuilder()
                    .clearLeadingComments())
                .setLocation(2, SUBSCRIBE_METHOD_LOCATION.toBuilder()
                    .clearLeadingComments())))
        .build();
    assertEquals(generator.buildServiceContexts(request).get(0), serviceContext);
  }

  @Test
  public void generate_noJavaPackage() throws Exception {
    ReactiveBleRpcGenerator.ServiceContext serviceContext = createServiceContext();
    serviceContext.methods.forEach(methodContext -> {
      methodContext.inputType = PROTO_PACKAGE + "." + METHOD_INPUT_TYPE;
      methodContext.outputType = PROTO_PACKAGE + "." + METHOD_OUTPUT_TYPE;
    });
    serviceContext.packageName = PROTO_PACKAGE;
    PluginProtos.CodeGeneratorRequest request = PluginProtos.CodeGeneratorRequest.newBuilder()
        .addFileToGenerate(FILE_NAME)
        .addProtoFile(FILE.toBuilder()
            .setOptions(FILE_OPTIONS.toBuilder()
                .clearJavaPackage()))
        .build();
    assertEquals(generator.buildServiceContexts(request).get(0), serviceContext);
  }

  @Test
  public void generate_wrongMethodNumberOfPath() throws Exception {
    ReactiveBleRpcGenerator.ServiceContext serviceContext = createServiceContext();
    serviceContext.methods = ImmutableList.of(createSubscribeMethodContext());
    PluginProtos.CodeGeneratorRequest request = PluginProtos.CodeGeneratorRequest.newBuilder()
        .addFileToGenerate(FILE_NAME)
        .addProtoFile(FILE.toBuilder()
            .setSourceCodeInfo(DescriptorProtos.SourceCodeInfo.newBuilder()
                .addLocation(SERVICE_LOCATION)
                .addLocation(DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                    .addAllPath(ImmutableList.of(6, 0, 2, 0, 0)))
                .addLocation(SUBSCRIBE_METHOD_LOCATION)))
        .build();
    assertEquals(generator.buildServiceContexts(request).get(0), serviceContext);
  }

  @Test
  public void generate_wrongMethodPathServiceNumber() throws Exception {
    ReactiveBleRpcGenerator.ServiceContext serviceContext = createServiceContext();
    serviceContext.methods = ImmutableList.of(createSubscribeMethodContext());
    PluginProtos.CodeGeneratorRequest request = PluginProtos.CodeGeneratorRequest.newBuilder()
        .addFileToGenerate(FILE_NAME)
        .addProtoFile(FILE.toBuilder()
            .setSourceCodeInfo(DescriptorProtos.SourceCodeInfo.newBuilder()
                .addLocation(SERVICE_LOCATION)
                .addLocation(DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                    .addAllPath(ImmutableList.of(5, 0, 2, 0)))
                .addLocation(SUBSCRIBE_METHOD_LOCATION)))
        .build();
    assertEquals(generator.buildServiceContexts(request).get(0), serviceContext);
  }

  @Test
  public void generate_wrongMethodPathServiceIndex() throws Exception {
    ReactiveBleRpcGenerator.ServiceContext serviceContext = createServiceContext();
    serviceContext.methods = ImmutableList.of(createSubscribeMethodContext());
    PluginProtos.CodeGeneratorRequest request = PluginProtos.CodeGeneratorRequest.newBuilder()
        .addFileToGenerate(FILE_NAME)
        .addProtoFile(FILE.toBuilder()
            .setSourceCodeInfo(DescriptorProtos.SourceCodeInfo.newBuilder()
                .addLocation(SERVICE_LOCATION)
                .addLocation(DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                    .addAllPath(ImmutableList.of(6, 1, 2, 0)))
                .addLocation(SUBSCRIBE_METHOD_LOCATION)))
        .build();
    assertEquals(generator.buildServiceContexts(request).get(0), serviceContext);
  }

  @Test
  public void generate_wrongMethodPathNumber() throws Exception {
    ReactiveBleRpcGenerator.ServiceContext serviceContext = createServiceContext();
    serviceContext.methods = ImmutableList.of(createSubscribeMethodContext());
    PluginProtos.CodeGeneratorRequest request = PluginProtos.CodeGeneratorRequest.newBuilder()
        .addFileToGenerate(FILE_NAME)
        .addProtoFile(FILE.toBuilder()
            .setSourceCodeInfo(DescriptorProtos.SourceCodeInfo.newBuilder()
                .addLocation(SERVICE_LOCATION)
                .addLocation(DescriptorProtos.SourceCodeInfo.Location.newBuilder()
                    .addAllPath(ImmutableList.of(6, 0, 3, 0)))
                .addLocation(SUBSCRIBE_METHOD_LOCATION)))
        .build();
    assertEquals(generator.buildServiceContexts(request).get(0), serviceContext);
  }

  private ReactiveBleRpcGenerator.ServiceContext createServiceContext() {
    ReactiveBleRpcGenerator.ServiceContext serviceContext = new ReactiveBleRpcGenerator.ServiceContext();
    serviceContext.serviceName = SERVICE_NAME;
    serviceContext.className = RX_CLASS_PREFIX + SERVICE_NAME;
    serviceContext.fileName = serviceContext.className + JAVA_SOURCE_EXTENSION;
    serviceContext.javaDoc = String.format(SERVICE_JAVADOC_TEMPLATE, SERVICE_JAVADOC);
    serviceContext.packageName = JAVA_PACKAGE;
    serviceContext.methods = ImmutableList.of(createReadMethodContext(), createSubscribeMethodContext());
    return serviceContext;
  }

  private ReactiveBleRpcGenerator.MethodContext createReadMethodContext() {
    ReactiveBleRpcGenerator.MethodContext readMethodContext = new ReactiveBleRpcGenerator.MethodContext();
    readMethodContext.methodName = READ_METHOD_NAME_FIRST_LOWER_CASE;
    readMethodContext.inputType = JAVA_PACKAGE + "." + METHOD_INPUT_TYPE;
    readMethodContext.outputType = JAVA_PACKAGE + "." + METHOD_OUTPUT_TYPE;
    readMethodContext.javaDoc = String.format(METHOD_JAVADOC_TEMPLATE, READ_METHOD_JAVADOC);
    return readMethodContext;
  }

  private ReactiveBleRpcGenerator.MethodContext createSubscribeMethodContext() {
    ReactiveBleRpcGenerator.MethodContext subscribeMethodContext = new ReactiveBleRpcGenerator.MethodContext();
    subscribeMethodContext.methodName = SUBSCRIBE_METHOD_NAME_FIRST_LOWER_CASE;
    subscribeMethodContext.inputType = JAVA_PACKAGE + "." + METHOD_INPUT_TYPE;
    subscribeMethodContext.outputType = JAVA_PACKAGE + "." + METHOD_OUTPUT_TYPE;
    subscribeMethodContext.javaDoc = String.format(METHOD_JAVADOC_TEMPLATE, SUBSCRIBE_METHOD_JAVADOC);
    subscribeMethodContext.isManyOutput = true;
    return subscribeMethodContext;
  }

  private void assertEquals(ReactiveBleRpcGenerator.ServiceContext firstService, ReactiveBleRpcGenerator.ServiceContext secondService) {
    assertThat(firstService.className).isEqualTo(secondService.className);
    assertThat(firstService.packageName).isEqualTo(secondService.packageName);
    assertThat(firstService.fileName).isEqualTo(secondService.fileName);
    assertThat(firstService.serviceName).isEqualTo(secondService.serviceName);
    assertThat(firstService.javaDoc).isEqualTo(secondService.javaDoc);
    assertThat(firstService.deprecated).isEqualTo(secondService.deprecated);
    for (int i = 0; i < firstService.methods.size(); i++) {
      assertEquals(firstService.methods.get(i), secondService.methods.get(i));
    }
  }

  private void assertEquals(ReactiveBleRpcGenerator.MethodContext firstMethod, ReactiveBleRpcGenerator.MethodContext secondMethod) {
    assertThat(firstMethod.outputType).isEqualTo(secondMethod.outputType);
    assertThat(firstMethod.inputType).isEqualTo(secondMethod.inputType);
    assertThat(firstMethod.methodName).isEqualTo(secondMethod.methodName);
    assertThat(firstMethod.javaDoc).isEqualTo(secondMethod.javaDoc);
    assertThat(firstMethod.isManyOutput).isEqualTo(secondMethod.isManyOutput);
    assertThat(firstMethod.deprecated).isEqualTo(secondMethod.deprecated);
  }
}
