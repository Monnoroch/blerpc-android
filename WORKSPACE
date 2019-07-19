workspace(name = "blerpc")

load("@bazel_tools//tools/build_defs/repo:java.bzl", "java_import_external")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "bazel_toolchains",
    sha256 = "82f2f7d158b0f67579c5649a73adb1b9bea3e385df6b4c4368a1b53eed11dbfe",
    strip_prefix = "bazel-toolchains-f2c9219be2dcbe4d766fdbe6aa470eced168ed03",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-toolchains/archive/f2c9219be2dcbe4d766fdbe6aa470eced168ed03.tar.gz",
        "https://github.com/bazelbuild/bazel-toolchains/archive/f2c9219be2dcbe4d766fdbe6aa470eced168ed03.tar.gz",
    ],
)

load("@bazel_toolchains//rules:rbe_repo.bzl", "rbe_autoconfig")

rbe_autoconfig(name = "rbe_default")

http_archive(
    name = "bazel_skylib",
    sha256 = "2e351c3b4861b0c5de8db86fdd100869b544c759161008cd93949dddcbfaba53",
    strip_prefix = "bazel-skylib-0.8.0",
    url = "https://github.com/bazelbuild/bazel-skylib/archive/0.8.0.zip",
)

http_archive(
    name = "build_stack_rules_proto",
    sha256 = "19ed4bd34fc4646eb33aa39c6605cc3359867df3347c2f60b9dd97751b8c0cdf",
    strip_prefix = "rules_proto-e3da19a360d7c565f0896a08c3f975829dbdc34d",
    urls = ["https://github.com/Monnoroch/rules_proto/archive/e3da19a360d7c565f0896a08c3f975829dbdc34d.tar.gz"],
)

http_archive(
    name = "com_google_protobuf",
    sha256 = "f976a4cd3f1699b6d20c1e944ca1de6754777918320c719742e1674fcf247b7e",
    strip_prefix = "protobuf-3.7.1",
    type = "zip",
    urls = ["https://github.com/protocolbuffers/protobuf/archive/v3.7.1.zip"],
)

bind(
    name = "zlib",
    actual = "@net_zlib//:zlib",
)

http_archive(
    name = "net_zlib",
    build_file = "@com_google_protobuf//:third_party/zlib.BUILD",
    sha256 = "c3e5e9fdd5004dcb542feda5ee4f0ff0744628baf8ed2dd5d66f8ca1197cb1a1",
    strip_prefix = "zlib-1.2.11",
    urls = ["https://zlib.net/zlib-1.2.11.tar.gz"],
)

java_import_external(
    name = "com_google_protobuf_protobuf_java",
    jar_sha256 = "b1c2d420d2833429d11e405a58251e13bd7e3f22c266b49227c41e4d21361286",
    jar_urls = [
        "https://repo1.maven.org/maven2/com/google/protobuf/protobuf-java/3.2.0/protobuf-java-3.2.0.jar",
    ],
    licenses = ["notice"],
)

java_import_external(
    name = "com_google_guava_guava",
    jar_sha256 = "a0e9cabad665bc20bcd2b01f108e5fc03f756e13aea80abaadb9f407033bea2c",
    jar_urls = [
        "https://repo1.maven.org/maven2/com/google/guava/guava/26.0-jre/guava-26.0-jre.jar",
    ],
    licenses = ["notice"],
    deps = [
        "@com_google_code_findbugs_jsr305",
        "@com_google_errorprone_error_prone_annotations",
        "@com_google_j2objc_j2objc_annotations",
    ],
)

java_import_external(
    name = "com_google_code_findbugs_jsr305",
    jar_sha256 = "bec0b24dcb23f9670172724826584802b80ae6cbdaba03bdebdef9327b962f6a",
    jar_urls = [
        "https://repo1.maven.org/maven2/com/google/code/findbugs/jsr305/3.0.0/jsr305-3.0.0.jar",
    ],
    licenses = ["notice"],
)

java_import_external(
    name = "com_google_errorprone_error_prone_annotations",
    jar_sha256 = "357cd6cfb067c969226c442451502aee13800a24e950fdfde77bcdb4565a668d",
    jar_urls = [
        "https://repo1.maven.org/maven2/com/google/errorprone/error_prone_annotations/2.3.2/error_prone_annotations-2.3.2.jar",
    ],
    licenses = ["notice"],
)

java_import_external(
    name = "com_google_j2objc_j2objc_annotations",
    jar_sha256 = "2994a7eb78f2710bd3d3bfb639b2c94e219cedac0d4d084d516e78c16dddecf6",
    jar_urls = [
        "https://repo1.maven.org/maven2/com/google/j2objc/j2objc-annotations/1.1/j2objc-annotations-1.1.jar",
    ],
    licenses = ["notice"],
)

java_import_external(
    name = "findbugs_annotations",
    jar_sha256 = "e04c8b4fc645bf773c866ad0ad2c6ea8a7a20b48004a118f2091122faff7d2f1",
    jar_urls = [
        "https://repo1.maven.org/maven2/findbugs/annotations/1.0.0/annotations-1.0.0.jar",
    ],
    licenses = ["notice"],
)

java_import_external(
    name = "com_github_spullara_mustache_java_compiler",
    jar_sha256 = "23fdcfb8b0b80425f94732e93e479ee5a485c4a31df0c32cc1d9d248e8abf7a5",
    jar_urls = [
        "https://repo1.maven.org/maven2/com/github/spullara/mustache/java/compiler/0.9.4/compiler-0.9.4.jar",
    ],
    licenses = ["notice"],
)

java_import_external(
    name = "com_salesforce_servicelibs_jprotoc",
    jar_sha256 = "a4fa6f9d44f3902441c2ea7564fd3a9021d35573c46fe50f680eb24356f887c3",
    jar_urls = [
        "https://repo1.maven.org/maven2/com/salesforce/servicelibs/jprotoc/0.8.0/jprotoc-0.8.0.jar",
    ],
    licenses = ["notice"],
    deps = [
        "@com_github_spullara_mustache_java_compiler",
        "@io_grpc_grpc_protobuf",
    ],
)

java_import_external(
    name = "com_google_api_grpc_proto_google_common_protos",
    jar_sha256 = "cfe1da4c0e82820c32a83c4bf25b42f4d3b7113177321c437a9fff3c42e1f4c9",
    jar_urls = [
        "https://repo1.maven.org/maven2/com/google/api/grpc/proto-google-common-protos/1.0.0/proto-google-common-protos-1.0.0.jar",
    ],
    licenses = ["notice"],
)

java_import_external(
    name = "io_grpc_grpc_protobuf",
    jar_sha256 = "792bbe5fc54272b9abb2102bb3ebc70e4a35d672690ed05e0726e6faa1c75bf4",
    jar_urls = [
        "https://repo1.maven.org/maven2/io/grpc/grpc-protobuf/1.15.0/grpc-protobuf-1.15.0.jar",
    ],
    licenses = ["notice"],
    deps = [
        "@com_google_api_grpc_proto_google_common_protos",
        "@com_google_guava_guava",
        "@com_google_protobuf_protobuf_java",
        "@io_grpc_grpc_core",
        "@io_grpc_grpc_protobuf_lite",
    ],
)

java_import_external(
    name = "io_grpc_grpc_core",
    jar_sha256 = "dd615ae3c01481e67adf8d346beb4979becc09af78b6662b52cc8395eb2255c0",
    jar_urls = [
        "https://repo1.maven.org/maven2/io/grpc/grpc-core/1.15.0/grpc-core-1.15.0.jar",
    ],
    licenses = ["notice"],
    deps = [
        "@com_google_code_findbugs_jsr305",
        "@com_google_code_gson_gson",
        "@com_google_errorprone_error_prone_annotations",
        "@com_google_guava_guava",
        "@io_grpc_grpc_context",
        "@io_opencensus_opencensus_api",
        "@io_opencensus_opencensus_contrib_grpc_metrics",
        "@org_codehaus_mojo_animal_sniffer_annotations",
    ],
)

java_import_external(
    name = "io_grpc_grpc_protobuf_lite",
    jar_sha256 = "e406279e30a4469d49398a363ae99682c46f8949b11d08b6dd32d7e0a7c964a6",
    jar_urls = [
        "https://repo1.maven.org/maven2/io/grpc/grpc-protobuf-lite/1.15.0/grpc-protobuf-lite-1.15.0.jar",
    ],
    licenses = ["notice"],
    deps = [
        "@com_google_guava_guava",
        "@io_grpc_grpc_core",
    ],
)

java_import_external(
    name = "com_google_code_gson_gson",
    jar_sha256 = "2d43eb5ea9e133d2ee2405cc14f5ee08951b8361302fdd93494a3a997b508d32",
    jar_urls = [
        "https://repo1.maven.org/maven2/com/google/code/gson/gson/2.7/gson-2.7.jar",
    ],
    licenses = ["notice"],
)

java_import_external(
    name = "io_grpc_grpc_context",
    jar_sha256 = "512e99587fa389d7ba7830d91f1e2f949162814ec077073cd4d6766fa63896f7",
    jar_urls = [
        "https://repo1.maven.org/maven2/io/grpc/grpc-context/1.15.0/grpc-context-1.15.0.jar",
    ],
    licenses = ["notice"],
)

java_import_external(
    name = "io_opencensus_opencensus_api",
    jar_sha256 = "8c1de62cbdaf74b01b969d1ed46c110bca1a5dd147c50a8ab8c5112f42ced802",
    jar_urls = [
        "https://repo1.maven.org/maven2/io/opencensus/opencensus-api/0.12.3/opencensus-api-0.12.3.jar",
    ],
    licenses = ["notice"],
    deps = [
        "@com_google_errorprone_error_prone_annotations",
    ],
)

java_import_external(
    name = "io_opencensus_opencensus_contrib_grpc_metrics",
    jar_sha256 = "632c1e1463db471b580d35bc4be868facbfbf0a19aa6db4057215d4a68471746",
    jar_urls = [
        "https://repo1.maven.org/maven2/io/opencensus/opencensus-contrib-grpc-metrics/0.12.3/opencensus-contrib-grpc-metrics-0.12.3.jar",
    ],
    licenses = ["notice"],
    deps = [
        "@com_google_errorprone_error_prone_annotations",
        "@io_opencensus_opencensus_api",
    ],
)

java_import_external(
    name = "org_codehaus_mojo_animal_sniffer_annotations",
    jar_sha256 = "92654f493ecfec52082e76354f0ebf87648dc3d5cec2e3c3cdb947c016747a53",
    jar_urls = [
        "https://repo1.maven.org/maven2/org/codehaus/mojo/animal-sniffer-annotations/1.17/animal-sniffer-annotations-1.17.jar",
    ],
    licenses = ["notice"],
)
