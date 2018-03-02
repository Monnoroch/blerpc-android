package com.blerpc;

import static com.blerpc.Assert.assertError;
import static com.google.common.truth.Truth.assertThat;

import com.blerpc.device.test.proto.TestMetadata;
import com.blerpc.device.test.proto.TestOptionsBool;
import com.blerpc.device.test.proto.TestOptionsDoubleValueRequest;
import com.blerpc.device.test.proto.TestOptionsEnum;
import com.blerpc.device.test.proto.TestOptionsEqualsIndexesRequest;
import com.blerpc.device.test.proto.TestOptionsFloatValueRequest;
import com.blerpc.device.test.proto.TestOptionsImage;
import com.blerpc.device.test.proto.TestOptionsImageRequest;
import com.blerpc.device.test.proto.TestOptionsInteger;
import com.blerpc.device.test.proto.TestOptionsLong;
import com.blerpc.device.test.proto.TestOptionsMessage;
import com.blerpc.device.test.proto.TestOptionsNegativeRangeRequest;
import com.blerpc.device.test.proto.TestOptionsNoBytesRangeRequest;
import com.blerpc.device.test.proto.TestOptionsRangeBiggerThanCountRequest;
import com.blerpc.device.test.proto.TestOptionsRangeIntersectRequest;
import com.blerpc.device.test.proto.TestOptionsSmallFourBytesEnumRangeRequest;
import com.blerpc.device.test.proto.TestOptionsSmallThreeBytesEnumRangeRequest;
import com.blerpc.device.test.proto.TestOptionsSmallTwoBytesEnumRangeRequest;
import com.blerpc.device.test.proto.TestOptionsStringValueRequest;
import com.blerpc.device.test.proto.TestOptionsWithFieldNoBytesRequest;
import com.blerpc.device.test.proto.TestOptionsWrongBooleanRangeRequest;
import com.blerpc.device.test.proto.TestOptionsWrongEnumRangeRequest;
import com.blerpc.device.test.proto.TestOptionsWrongIntegerRangeRequest;
import com.blerpc.device.test.proto.TestOptionsWrongLongRangeRequest;
import com.blerpc.device.test.proto.TestOptionsZeroBytesRequest;
import com.blerpc.device.test.proto.TestToken;
import com.blerpc.device.test.proto.TestType;
import java.nio.ByteOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link AnnotationMessageConverter}.
 */
@RunWith(MockitoJUnitRunner.class)
public class AnnotationMessageConverterTest {

    private static final TestOptionsImage IMAGE_REQUEST_1 = TestOptionsImage.newBuilder()
            .setVersion(20)
            .setCrc(500)
            .setLength(100000)
            .setRelease(true)
            .setType(TestType.FULL)
            .setMetadata(TestMetadata.newBuilder()
                    .setBleMetadata(20000)
                    .setToken(TestToken.newBuilder()
                            .setToken(30000)))
            .setBuildTime(1519548579757L)
            .build();
    private static final TestOptionsImage IMAGE_REQUEST_2 = TestOptionsImage.newBuilder()
            .setVersion(35)
            .setCrc(800)
            .setLength(130500)
            .setRelease(false)
            .setType(TestType.ONLY_APP)
            .setMetadata(TestMetadata.newBuilder()
                    .setBleMetadata(40000)
                    .setToken(TestToken.newBuilder()
                            .setToken(50000)))
            .setBuildTime(1519576271989L)
            .build();
    private static final TestOptionsInteger TEST_OPTIONS_INTEGER = TestOptionsInteger.newBuilder()
            .setValue(100000)
            .build();
    private static final TestOptionsLong TEST_OPTIONS_LONG = TestOptionsLong.newBuilder()
            .setValue(10000000000L)
            .build();
    private static final TestOptionsBool TEST_OPTIONS_BOOL = TestOptionsBool.newBuilder()
            .setValue(true)
            .build();
    private static final TestOptionsEnum TEST_OPTIONS_ENUM = TestOptionsEnum.newBuilder()
            .setType(TestType.FULL)
            .build();
    private static final TestOptionsMessage TEST_OPTIONS_MESSAGE = TestOptionsMessage.newBuilder()
            .setValueMessage(TEST_OPTIONS_INTEGER)
            .build();
    private static final byte[] IMAGE_REQUEST_1_BYTES_LITTLE_ENDIAN =
            new byte[]{20, -12, 1, -96, -122, 1, 0, 1, 2, 0, 32, 78, 0, 0, 0, 48, 117, 0, 0, 0, -83, 63, 39, -52, 97, 1, 0, 0};
    private static final byte[] IMAGE_REQUEST_1_BYTES_BIG_ENDIAN =
            new byte[]{20, 1, -12, 0, 1, -122, -96, 1, 2, 0, 0, 0, 78, 32, 0, 0, 0, 117, 48, 0, 0, 0, 1, 97, -52, 39, 63, -83};
    private static final byte[] IMAGE_REQUEST_2_BYTES_LITTLE_ENDIAN =
            new byte[]{35, 32, 3, -60, -3, 1, 0, 0, 1, 0, 64, -100, 0, 0, 0, 80, -61, 0, 0, 0, 117, -52, -51, -51, 97, 1, 0, 0};
    private static final byte[] IMAGE_REQUEST_2_BYTES_BIG_ENDIAN =
            new byte[]{35, 3, 32, 0, 1, -3, -60, 0, 1, 0, 0, 0, -100, 64, 0, 0, 0, -61, 80, 0, 0, 0, 1, 97, -51, -51, -52, 117};
    private static final byte[] TEST_OPTIONS_INTEGER_BYTES_LITTLE_ENDIAN = new byte[]{-96, -122, 1, 0};
    private static final byte[] TEST_OPTIONS_INTEGER_BYTES_BIG_ENDIAN = new byte[]{0, 1, -122, -96};
    private static final byte[] TEST_OPTIONS_LONG_BYTES_LITTLE_ENDIAN = new byte[]{0, -28, 11, 84, 2, 0, 0, 0};
    private static final byte[] TEST_OPTIONS_LONG_BYTES_BIG_ENDIAN = new byte[]{0, 0, 0, 2, 84, 11, -28, 0};
    private static final byte[] TEST_OPTIONS_BOOL_BYTES = new byte[]{1};
    private static final byte[] TEST_OPTIONS_ENUM_BYTES = new byte[]{2};
    private static final byte[] TEST_OPTIONS_MESSAGE_BYTES_LITTLE_ENDIAN = new byte[]{-96, -122, 1, 0};
    private static final byte[] TEST_OPTIONS_MESSAGE_BYTES_BIG_ENDIAN = new byte[]{0, 1, -122, -96};

    AnnotationMessageConverter converter = new AnnotationMessageConverter();
    AnnotationMessageConverter converterLittleEndian = new AnnotationMessageConverter(ByteOrder.LITTLE_ENDIAN);

    @Test
    public void serializeRequestTest() throws Exception {
        assertThat(converterLittleEndian.serializeRequest(null, IMAGE_REQUEST_1)).isEqualTo(IMAGE_REQUEST_1_BYTES_LITTLE_ENDIAN);
        assertThat(converterLittleEndian.serializeRequest(null, IMAGE_REQUEST_2)).isEqualTo(IMAGE_REQUEST_2_BYTES_LITTLE_ENDIAN);
        assertThat(converterLittleEndian.serializeRequest(null, TEST_OPTIONS_INTEGER)).isEqualTo(TEST_OPTIONS_INTEGER_BYTES_LITTLE_ENDIAN);
        assertThat(converterLittleEndian.serializeRequest(null, TEST_OPTIONS_LONG)).isEqualTo(TEST_OPTIONS_LONG_BYTES_LITTLE_ENDIAN);
        assertThat(converterLittleEndian.serializeRequest(null, TEST_OPTIONS_BOOL)).isEqualTo(TEST_OPTIONS_BOOL_BYTES);
        assertThat(converterLittleEndian.serializeRequest(null, TEST_OPTIONS_ENUM)).isEqualTo(TEST_OPTIONS_ENUM_BYTES);
        assertThat(converterLittleEndian.serializeRequest(null, TEST_OPTIONS_MESSAGE)).isEqualTo(TEST_OPTIONS_MESSAGE_BYTES_LITTLE_ENDIAN);
        assertThat(converter.serializeRequest(null, IMAGE_REQUEST_1)).isEqualTo(IMAGE_REQUEST_1_BYTES_BIG_ENDIAN);
        assertThat(converter.serializeRequest(null, IMAGE_REQUEST_2)).isEqualTo(IMAGE_REQUEST_2_BYTES_BIG_ENDIAN);
        assertThat(converter.serializeRequest(null, TEST_OPTIONS_INTEGER)).isEqualTo(TEST_OPTIONS_INTEGER_BYTES_BIG_ENDIAN);
        assertThat(converter.serializeRequest(null, TEST_OPTIONS_LONG)).isEqualTo(TEST_OPTIONS_LONG_BYTES_BIG_ENDIAN);
        assertThat(converter.serializeRequest(null, TEST_OPTIONS_BOOL)).isEqualTo(TEST_OPTIONS_BOOL_BYTES);
        assertThat(converter.serializeRequest(null, TEST_OPTIONS_ENUM)).isEqualTo(TEST_OPTIONS_ENUM_BYTES);
        assertThat(converter.serializeRequest(null, TEST_OPTIONS_MESSAGE)).isEqualTo(TEST_OPTIONS_MESSAGE_BYTES_BIG_ENDIAN);
    }

    @Test
    public void serializeRequestTest_emptyMessage() throws Exception {
        assertThat(converter.serializeRequest(null, TestOptionsImageRequest.getDefaultInstance())).isEmpty();
    }

    @Test
    public void serializeRequestTest_zeroBytesMessage() throws Exception {
        assertThat(converter.serializeRequest(null, TestOptionsZeroBytesRequest.getDefaultInstance())).isEmpty();
    }

    @Test
    public void serializeRequestTest_messageWithFieldAndWithoutByteSize() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestOptionsWithFieldNoBytesRequest.getDefaultInstance()),
                "A non empty message TestOptionsWithFieldNoBytesRequest doesn't have com.blerpc.message_size annotation.");
    }

    @Test
    public void serializeRequestTest_noByteRangeMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestOptionsNoBytesRangeRequest.newBuilder()
                .setValue(20)
                .build()),
                "Proto field value doesn't have com.blerpc.field_bytes annotation");
    }

    @Test
    public void serializeRequestTest_equalsIndexesMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestOptionsEqualsIndexesRequest.newBuilder()
                .setMetadata(20000)
                .build()),
                "Field metadata has from_bytes = 0 which must be less than to_bytes = 0");
    }

    @Test
    public void serializeRequestTest_negativeRangeMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestOptionsNegativeRangeRequest.newBuilder()
                .setMetadata(20000)
                .build()),
                "Field metadata has from_bytes = -1 which is less than zero");
    }

    @Test
    public void serializeRequestTest_rangeBiggerThanCountMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestOptionsRangeBiggerThanCountRequest.newBuilder()
                .setMetadata(20000)
                .build()),
                "Field metadata has to_bytes = 11 which is bigger than message bytes size = 10");
    }

    @Test
    public void serializeRequestTest_rangeIntersectMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestOptionsRangeIntersectRequest.newBuilder()
                .setValue(20000)
                .setMetadata(30000)
                .build()),
                "Field value bytes range [0, 4] intersects with another field metadata bytes range [2, 6]");
    }

    @Test
    public void serializeRequestTest_unsupportedType() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestOptionsStringValueRequest.newBuilder()
                .setMessage("Message")
                .build()), "Unsupported field type: STRING");
        assertError(() -> converter.serializeRequest(null, TestOptionsFloatValueRequest.newBuilder()
                .setWeight(85.5f)
                .build()), "Unsupported field type: FLOAT");
        assertError(() -> converter.serializeRequest(null, TestOptionsDoubleValueRequest.newBuilder()
                .setImpedance(300.5678d)
                .build()), "Unsupported field type: DOUBLE");
    }

    @Test
    public void serializeRequestTest_wrongIntegerRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestOptionsWrongIntegerRangeRequest.newBuilder()
                .setValue(20)
                .build()),
                "Integer field value has unsupported size 11. Only sizes in [1, 8] are supported.");
    }

    @Test
    public void serializeRequestTest_wrongLongRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestOptionsWrongLongRangeRequest.newBuilder()
                .setValue(100000)
                .build()),
                "Integer field value has unsupported size 13. Only sizes in [1, 8] are supported.");
    }

    @Test
    public void serializeRequestTest_wrongEnumRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestOptionsWrongEnumRangeRequest.newBuilder()
                .setType(TestType.ONLY_APP)
                .build()),
                "Enum TestType field type has unsupported size 9. Only sizes in [1, 4] are supported.");
    }

    @Test
    public void serializeRequestTest_wrongBooleanRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestOptionsWrongBooleanRangeRequest.newBuilder()
                .setRelease(true)
                .build()),
                "Boolean field release has bytes size = 2, but has to be of size 1");
    }

    @Test
    public void serializeRequestTest_notEnoughRangeForEnum() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestOptionsSmallTwoBytesEnumRangeRequest.newBuilder()
                .setBigEnumValue(2)
                .build()),
                "1 byte(s) not enough for TestTwoBytesSizeEnum enum that has 2222 max number");
        assertError(() -> converter.serializeRequest(null, TestOptionsSmallThreeBytesEnumRangeRequest.newBuilder()
                .setBigEnumValue(2)
                .build()),
                "2 byte(s) not enough for TestThreeBytesSizeEnum enum that has 222222 max number");
        assertError(() -> converter.serializeRequest(null, TestOptionsSmallFourBytesEnumRangeRequest.newBuilder()
                .setBigEnumValue(2)
                .build()),
                "3 byte(s) not enough for TestFourBytesSizeEnum enum that has 222222222 max number");
    }

    @Test
    public void deserializeResponseTest() throws Exception {
        assertThat(converterLittleEndian.deserializeResponse(null, TestOptionsImage.getDefaultInstance(), IMAGE_REQUEST_1_BYTES_LITTLE_ENDIAN))
                .isEqualTo(IMAGE_REQUEST_1);
        assertThat(converter.deserializeResponse(null, TestOptionsImage.getDefaultInstance(), IMAGE_REQUEST_1_BYTES_BIG_ENDIAN))
                .isEqualTo(IMAGE_REQUEST_1);
        assertThat(converterLittleEndian.deserializeResponse(null, TestOptionsImage.getDefaultInstance(), IMAGE_REQUEST_2_BYTES_LITTLE_ENDIAN))
                .isEqualTo(IMAGE_REQUEST_2);
        assertThat(converter.deserializeResponse(null, TestOptionsImage.getDefaultInstance(), IMAGE_REQUEST_2_BYTES_BIG_ENDIAN))
                .isEqualTo(IMAGE_REQUEST_2);
        assertThat(converterLittleEndian.deserializeResponse(null, TestOptionsInteger.getDefaultInstance(),
                TEST_OPTIONS_INTEGER_BYTES_LITTLE_ENDIAN))
                .isEqualTo(TEST_OPTIONS_INTEGER);
        assertThat(converter.deserializeResponse(null, TestOptionsInteger.getDefaultInstance(),
                TEST_OPTIONS_INTEGER_BYTES_BIG_ENDIAN))
                .isEqualTo(TEST_OPTIONS_INTEGER);
        assertThat(converterLittleEndian.deserializeResponse(null, TestOptionsLong.getDefaultInstance(),
                TEST_OPTIONS_LONG_BYTES_LITTLE_ENDIAN))
                .isEqualTo(TEST_OPTIONS_LONG);
        assertThat(converter.deserializeResponse(null, TestOptionsLong.getDefaultInstance(),
                TEST_OPTIONS_LONG_BYTES_BIG_ENDIAN))
                .isEqualTo(TEST_OPTIONS_LONG);
        assertThat(converterLittleEndian.deserializeResponse(null, TestOptionsBool.getDefaultInstance(),
                TEST_OPTIONS_BOOL_BYTES))
                .isEqualTo(TEST_OPTIONS_BOOL);
        assertThat(converter.deserializeResponse(null, TestOptionsBool.getDefaultInstance(),
                TEST_OPTIONS_BOOL_BYTES))
                .isEqualTo(TEST_OPTIONS_BOOL);
        assertThat(converterLittleEndian.deserializeResponse(null, TestOptionsEnum.getDefaultInstance(),
                TEST_OPTIONS_ENUM_BYTES))
                .isEqualTo(TEST_OPTIONS_ENUM);
        assertThat(converter.deserializeResponse(null, TestOptionsEnum.getDefaultInstance(),
                TEST_OPTIONS_ENUM_BYTES))
                .isEqualTo(TEST_OPTIONS_ENUM);
        assertThat(converterLittleEndian.deserializeResponse(null, TestOptionsMessage.getDefaultInstance(),
                TEST_OPTIONS_MESSAGE_BYTES_LITTLE_ENDIAN))
                .isEqualTo(TEST_OPTIONS_MESSAGE);
        assertThat(converter.deserializeResponse(null, TestOptionsMessage.getDefaultInstance(),
                TEST_OPTIONS_MESSAGE_BYTES_BIG_ENDIAN))
                .isEqualTo(TEST_OPTIONS_MESSAGE);
    }

    @Test
    public void deserializeResponseTest_emptyMessage() throws Exception {
        assertThat(converter.deserializeResponse(null, TestOptionsImageRequest.getDefaultInstance(), new byte[0]))
                .isEqualTo(TestOptionsImageRequest.getDefaultInstance());
    }

    @Test
    public void deserializeResponseTest_wrongMessageByteSize() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestOptionsImage.getDefaultInstance(), new byte[10]),
                "Declared size 28 of message TestOptionsImage is not equal to device response size 10");
    }

    @Test
    public void deserializeResponseTest_messageWithFieldAndWithoutByteSize() throws Exception {
        assertError(() -> converter.deserializeResponse(null,
                TestOptionsWithFieldNoBytesRequest.getDefaultInstance(),
                IMAGE_REQUEST_1_BYTES_LITTLE_ENDIAN),
                "A non empty message TestOptionsWithFieldNoBytesRequest doesn't have com.blerpc.message_size annotation.");
    }

    @Test
    public void deserializeResponseTest_noByteRangeMessage() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestOptionsNoBytesRangeRequest.getDefaultInstance(), new byte[2]),
                "Proto field value doesn't have com.blerpc.field_bytes annotation");
    }

    @Test
    public void deserializeResponseTest_equalsIndexesMessage() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestOptionsEqualsIndexesRequest.getDefaultInstance(), new byte[10]),
                "Field metadata has from_bytes = 0 which must be less than to_bytes = 0");
    }

    @Test
    public void deserializeResponseTest_negativeRangeMessage() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestOptionsNegativeRangeRequest.getDefaultInstance(), new byte[10]),
                "Field metadata has from_bytes = -1 which is less than zero");
    }

    @Test
    public void deserializeResponseTest_rangeBiggerThanCountMessage() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestOptionsRangeBiggerThanCountRequest.getDefaultInstance(), new byte[10]),
                "Field metadata has to_bytes = 11 which is bigger than message bytes size = 10");
    }

    @Test
    public void deserializeResponseTest_unsupportedType() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestOptionsStringValueRequest.getDefaultInstance(), new byte[4]),
                "Unsupported field type: STRING");
        assertError(() -> converter.deserializeResponse(null, TestOptionsFloatValueRequest.getDefaultInstance(), new byte[4]),
                "Unsupported field type: FLOAT");
        assertError(() -> converter.deserializeResponse(null, TestOptionsDoubleValueRequest.getDefaultInstance(), new byte[4]),
                "Unsupported field type: DOUBLE");
    }

    @Test
    public void deserializeResponseTest_wrongIntegerRange() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestOptionsWrongIntegerRangeRequest.getDefaultInstance(), new byte[11]),
                "Integer field value has unsupported size 11. Only sizes in [1, 8] are supported.");
    }

    @Test
    public void deserializeResponseTest_wrongLongRange() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestOptionsWrongLongRangeRequest.getDefaultInstance(), new byte[13]),
                "Integer field value has unsupported size 13. Only sizes in [1, 8] are supported.");
    }

    @Test
    public void deserializeResponseTest_wrongEnumRange() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestOptionsWrongEnumRangeRequest.getDefaultInstance(), new byte[9]),
                "Enum TestType field type has unsupported size 9. Only sizes in [1, 4] are supported.");
    }

    @Test
    public void deserializeResponseTest_wrongBooleanRange() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestOptionsWrongBooleanRangeRequest.getDefaultInstance(), new byte[2]),
                "Boolean field release has bytes size = 2, but has to be of size 1");
    }
}
