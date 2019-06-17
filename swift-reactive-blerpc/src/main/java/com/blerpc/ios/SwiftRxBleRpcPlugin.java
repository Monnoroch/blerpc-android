package com.blerpc.ios;

import com.blerpc.proto.Blerpc;
import com.blerpc.ios.MessageGenerator.MessageContext;
import com.blerpc.ios.ServiceGenerator.ServiceContext;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse.File;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.salesforce.jprotoc.Generator;
import com.salesforce.jprotoc.GeneratorException;
import com.salesforce.jprotoc.ProtocPlugin;
import java.nio.file.Paths;
import java.util.stream.Stream;

/** Protoc generator plugin that generate Swift Rx wrappers for BleRpc services. */
public class SwiftRxBleRpcPlugin extends Generator {

  private static final String TEMPLATE_FILE_MESSAGE_PARSER = "MessageGeneratorTemplate.mustache";
  private static final String TEMPLATE_FILE_SERVICE = "ServiceGeneratorTemplate.mustache";

  public static void main(String[] args) {
    ProtocPlugin.generate(ImmutableList.of(new SwiftRxBleRpcPlugin()),
            ImmutableList.of(Blerpc.message,
                    Blerpc.field,
                    Blerpc.characteristic));
  }

  // TODO(#36): add validation for requests and responses.
  @Override
  public Stream<File> generate(CodeGeneratorRequest request) throws GeneratorException {
    ServiceGenerator serviceGenerator = new ServiceGenerator();
    Stream<ServiceContext> services = serviceGenerator.buildServiceContexts(request);

    MessageGenerator messageGenerator = new MessageGenerator();
    Stream<MessageContext> messages = messageGenerator.buildMessageContexts(request);

    Stream<File> serviceFiles = services.map(this::buildServiceOutputFile);
    Stream<File> messageFiles = messages.map(this::buildMessageOutputFile);

    return Stream.concat(serviceFiles, messageFiles);
  }

  private File buildServiceOutputFile(ServiceContext context) {
    return File.newBuilder()
        .setName(fullFileName(context))
        .setContent(applyTemplate(TEMPLATE_FILE_SERVICE, context))
        .build();
  }

  private File buildMessageOutputFile(MessageContext context) {
    return File.newBuilder()
            .setName(fullFileName(context))
            .setContent(applyTemplate(TEMPLATE_FILE_MESSAGE_PARSER, context))
            .build();
  }

  private String fullFileName(ServiceContext context) {
    return Paths.get(context.packageName
            .replace(".", java.io.File.separator), context.fileName).toString();
  }

  private String fullFileName(MessageContext context) {
    return Paths.get(context.packageName
            .replace(".", java.io.File.separator), context.fileName).toString();
  }

}
