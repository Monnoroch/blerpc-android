package com.blerpc.ios;

import com.blerpc.proto.Blerpc;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.compiler.PluginProtos;
import com.salesforce.jprotoc.Generator;
import com.salesforce.jprotoc.GeneratorException;
import com.salesforce.jprotoc.ProtocPlugin;
import java.io.File;
import java.nio.file.Paths;
import java.util.stream.Stream;

/** Protoc generator plugin that generate Swift PromiseKit wrappers for BleRpc services. */
public class SwiftPromiseKitBleRpcPlugin extends Generator {

  private static final String TEMPLATE_FILE_MESSAGE_PARSER = "MessageGeneratorTemplate.mustache";
  private static final String TEMPLATE_FILE_SERVICE = "ServiceGeneratorTemplate.mustache";

  public static void main(String[] args) {
    ProtocPlugin.generate(ImmutableList.of(new SwiftPromiseKitBleRpcPlugin()),
            ImmutableList.of(Blerpc.message,
                    Blerpc.field,
                    Blerpc.characteristic));
  }

  // TODO(#36): add validation for requests and responses.
  @Override
  public Stream<PluginProtos.CodeGeneratorResponse.File> generate(
      PluginProtos.CodeGeneratorRequest request) throws GeneratorException {
    ServiceGenerator serviceGenerator = new ServiceGenerator();
    Stream<ServiceGenerator.ServiceContext> services = serviceGenerator.buildServiceContexts(request);

    MessageGenerator messageGenerator = new MessageGenerator();
    Stream<MessageGenerator.MessageContext> messages = messageGenerator.buildMessageContexts(request);

    Stream<PluginProtos.CodeGeneratorResponse.File> serviceFiles = services.map(this::buildServiceOutputFile);
    Stream<PluginProtos.CodeGeneratorResponse.File> messageFiles = messages.map(this::buildMessageOutputFile);

    return Stream.concat(serviceFiles, messageFiles);
  }

  private PluginProtos.CodeGeneratorResponse.File buildServiceOutputFile(ServiceGenerator.ServiceContext context) {
    return PluginProtos.CodeGeneratorResponse.File.newBuilder()
        .setName(fullFileName(context))
        .setContent(applyTemplate(TEMPLATE_FILE_SERVICE, context))
        .build();
  }

  private PluginProtos.CodeGeneratorResponse.File buildMessageOutputFile(MessageGenerator.MessageContext context) {
    return PluginProtos.CodeGeneratorResponse.File.newBuilder()
            .setName(fullFileName(context))
            .setContent(applyTemplate(TEMPLATE_FILE_MESSAGE_PARSER, context))
            .build();
  }

  private String fullFileName(ServiceGenerator.ServiceContext context) {
    return Paths.get(context.packageName.replace(".", File.separator), context.fileName).toString();
  }

  private String fullFileName(MessageGenerator.MessageContext context) {
    return Paths.get(context.packageName.replace(".", File.separator), context.fileName).toString();
  }

}
