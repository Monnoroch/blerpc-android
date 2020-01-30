package com.blerpc.ios;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Helpers for generator. */
public class Common {

    private static final String SWIFT_PACKAGE_TO_CLASS_SAPARATOR = "_";
    private static final String PROTO_PACKAGE_SEPARATOR = "\\.";

    /**
     * Checks if proto file has package or not.
     * @param file - input proto file.
     * @return boolean indicating if proto file has package or not.
     */
    static public boolean hasPackage(FileDescriptorProto file) {
        return !file.getPackage().isEmpty();
    }

    /**
     * Extracts package name from proto file.
     * @param proto - input proto file.
     * @return extracted package name as string.
     */
    static public String extractPackageName(FileDescriptorProto proto) {
        String javaPackage = proto.getOptions().getJavaPackage();
        return javaPackage.isEmpty() ? proto.getPackage() : javaPackage;
    }

    /**
     * Extracts package name from proto file and make it Swift format.
     * @param proto - input proto file.
     * @return extracted package name as string and formatted for Swift.
     */
    static public String extractSwiftPackageName(FileDescriptorProto proto) {
        String packageName = proto.getPackage();
        String[] splittedPackageName = packageName.split(PROTO_PACKAGE_SEPARATOR);
        return Arrays.stream(splittedPackageName)
                .map(key -> upperCaseFirstLetter(key) + SWIFT_PACKAGE_TO_CLASS_SAPARATOR)
                .collect(Collectors.joining());
    }

    /**
     * Uppercase first letter.
     * @param string - input string which need to be uppercased.
     * @return uppercased string.
     */
    static public String upperCaseFirstLetter(String string) {
        if (string.isEmpty())
        {
            return "";
        }
        return Character.toUpperCase(string.charAt(0)) + (string.length() > 1 ? string.substring(1) : "");
    }

    /**
     * Lowercase first letter.
     * @param string - input string which need to be lowercased.
     * @return lowercased string.
     */
    static public String lowerCaseFirstLetter(String string) {
        if (string.isEmpty())
        {
            return "";
        }
        return Character.toLowerCase(string.charAt(0))
                + (string.length() > 1 ? string.substring(1) : "");
    }

    /**
     * Generates class type in Swift format.
     * @param protoFile - proto file in with which to operate.
     * @param type - type which need to convert in Swift type.
     * @return string which represents proto type in Swift format.
     */
    static public String generateSwiftProtoType(FileDescriptorProto protoFile, String type) {
        return extractSwiftPackageName(protoFile)
                + type.split(PROTO_PACKAGE_SEPARATOR)[type.split(PROTO_PACKAGE_SEPARATOR).length - 1];
    }

    /**
     * Check if input request is empty and if not - throws error. Used for Read/Subscribe methods where requests must be empty.
     * @param protoFile - proto file in with which to operate.
     * @param inputType - request type.
     */
    static public void checkIsEmptyRequest(FileDescriptorProto protoFile, String inputType) {
        protoFile.getMessageTypeList().forEach((message) -> {
            if (generateSwiftProtoType(protoFile, message.getName()).equals(inputType)) {
                checkArgument(
                        message.getFieldCount() == 0,
                        "BleRpc doesn't support non empty requests for read/subscribe methods.");
            }
        });
    }
}
