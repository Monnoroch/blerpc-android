package com.blerpc;

import static com.blerpc.Assert.assertError;
import static com.google.common.truth.Truth.assertThat;

import com.blerpc.device.test.proto.TestBoolMessage;
import com.blerpc.device.test.proto.TestDoubleValueMessage;
import com.blerpc.device.test.proto.TestEmbeddedMessage;
import com.blerpc.device.test.proto.TestEmptyMessage;
import com.blerpc.device.test.proto.TestEnum;
import com.blerpc.device.test.proto.TestEnumMessage;
import com.blerpc.device.test.proto.TestFloatValueMessage;
import com.blerpc.device.test.proto.TestIntegerMessage;
import com.blerpc.device.test.proto.TestLongMessage;
import com.blerpc.device.test.proto.TestMessage;
import com.blerpc.device.test.proto.TestNegativeRangeMessage;
import com.blerpc.device.test.proto.TestNoBytesRangeMessage;
import com.blerpc.device.test.proto.TestNoBytesSizeMessage;
import com.blerpc.device.test.proto.TestNonPrimitiveMessage;
import com.blerpc.device.test.proto.TestRangeBiggerThanCountMessage;
import com.blerpc.device.test.proto.TestRangesIntersectMessage;
import com.blerpc.device.test.proto.TestSmallFourBytesEnumRangeMessage;
import com.blerpc.device.test.proto.TestSmallThreeBytesEnumRangeMessage;
import com.blerpc.device.test.proto.TestSmallTwoBytesEnumRangeMessage;
import com.blerpc.device.test.proto.TestStringValueMessage;
import com.blerpc.device.test.proto.TestWrongBooleanRangeMessage;
import com.blerpc.device.test.proto.TestWrongEnumRangeMessage;
import com.blerpc.device.test.proto.TestWrongIntegerRangeMessage;
import com.blerpc.device.test.proto.TestWrongLongRangeMessage;
import com.blerpc.device.test.proto.TestZeroBytesMessage;
import com.blerpc.device.test.proto.TestZeroSizeRangeMessage;
import java.nio.ByteOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link AnnotationMessageConverter}.
 */
@RunWith(MockitoJUnitRunner.class)
public class AnnotationMessageConverterTest {

    private static final TestMessage IMAGE_REQUEST_1 = TestMessage.newBuilder()
            .setByteValue(20)
            .setShortValue(500)
            .setIntValue(100000)
            .setBoolValue(true)
            .setEnumValue(TestEnum.VALUE_2)
            .setEmbeddedMessage(TestEmbeddedMessage.newBuilder()
                    .setValue(20000))
            .setLongValue(1519548579757L)
            .build();
    private static final TestMessage IMAGE_REQUEST_2 = TestMessage.newBuilder()
            .setByteValue(35)
            .setShortValue(800)
            .setIntValue(130500)
            .setBoolValue(false)
            .setEnumValue(TestEnum.VALUE_1)
            .setEmbeddedMessage(TestEmbeddedMessage.newBuilder()
                    .setValue(40000))
            .setLongValue(1519576271989L)
            .build();
    private static final TestIntegerMessage TEST_OPTIONS_INTEGER = TestIntegerMessage.newBuilder()
            .setValue(100000)
            .build();
    private static final TestLongMessage TEST_OPTIONS_LONG = TestLongMessage.newBuilder()
            .setValue(10000000000L)
            .build();
    private static final TestBoolMessage TEST_OPTIONS_BOOL = TestBoolMessage.newBuilder()
            .setValue(true)
            .build();
    private static final TestEnumMessage TEST_OPTIONS_ENUM = TestEnumMessage.newBuilder()
            .setType(TestEnum.VALUE_2)
            .build();
    private static final TestNonPrimitiveMessage TEST_NON_PRIMITIVE_MESSAGE = TestNonPrimitiveMessage.newBuilder()
            .setValueMessage(TEST_OPTIONS_INTEGER)
            .build();
    private static final byte[] IMAGE_REQUEST_1_BYTES_LITTLE_ENDIAN =
            new byte[]{20, -12, 1, -96, -122, 1, 0, 1, 2, 0, 32, 78, 0, 0, -83, 63, 39, -52, 97, 1, 0, 0};
    private static final byte[] IMAGE_REQUEST_1_BYTES_BIG_ENDIAN =
            new byte[]{20, 1, -12, 0, 1, -122, -96, 1, 2, 0, 0, 0, 78, 32, 0, 0, 1, 97, -52, 39, 63, -83};
    private static final byte[] IMAGE_REQUEST_2_BYTES_LITTLE_ENDIAN =
            new byte[]{35, 32, 3, -60, -3, 1, 0, 0, 1, 0, 64, -100, 0, 0, 117, -52, -51, -51, 97, 1, 0, 0};
    private static final byte[] IMAGE_REQUEST_2_BYTES_BIG_ENDIAN =
            new byte[]{35, 3, 32, 0, 1, -3, -60, 0, 1, 0, 0, 0, -100, 64, 0, 0, 1, 97, -51, -51, -52, 117};
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
        assertThat(converterLittleEndian.serializeRequest(null, TEST_NON_PRIMITIVE_MESSAGE)).isEqualTo(TEST_OPTIONS_MESSAGE_BYTES_LITTLE_ENDIAN);
        assertThat(converter.serializeRequest(null, IMAGE_REQUEST_1)).isEqualTo(IMAGE_REQUEST_1_BYTES_BIG_ENDIAN);
        assertThat(converter.serializeRequest(null, IMAGE_REQUEST_2)).isEqualTo(IMAGE_REQUEST_2_BYTES_BIG_ENDIAN);
        assertThat(converter.serializeRequest(null, TEST_OPTIONS_INTEGER)).isEqualTo(TEST_OPTIONS_INTEGER_BYTES_BIG_ENDIAN);
        assertThat(converter.serializeRequest(null, TEST_OPTIONS_LONG)).isEqualTo(TEST_OPTIONS_LONG_BYTES_BIG_ENDIAN);
        assertThat(converter.serializeRequest(null, TEST_OPTIONS_BOOL)).isEqualTo(TEST_OPTIONS_BOOL_BYTES);
        assertThat(converter.serializeRequest(null, TEST_OPTIONS_ENUM)).isEqualTo(TEST_OPTIONS_ENUM_BYTES);
        assertThat(converter.serializeRequest(null, TEST_NON_PRIMITIVE_MESSAGE)).isEqualTo(TEST_OPTIONS_MESSAGE_BYTES_BIG_ENDIAN);
    }

    @Test
    public void serializeRequestTest_emptyMessage() throws Exception {
        assertThat(converter.serializeRequest(null, TestEmptyMessage.getDefaultInstance())).isEmpty();
    }

    @Test
    public void serializeRequestTest_zeroBytesMessage() throws Exception {
        assertThat(converter.serializeRequest(null, TestZeroBytesMessage.getDefaultInstance())).isEmpty();
    }

    @Test
    public void serializeRequestTest_messageWithFieldAndWithoutByteSize() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestNoBytesSizeMessage.getDefaultInstance()),
                "A non empty message TestNoBytesSizeMessage doesn't have com.blerpc.message_size annotation.");
    }

    @Test
    public void serializeRequestTest_noByteRangeMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestNoBytesRangeMessage.getDefaultInstance()),
                "Proto field value doesn't have com.blerpc.field_bytes annotation");
    }

    @Test
    public void serializeRequestTest_zeroSizeRangeMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestZeroSizeRangeMessage.getDefaultInstance()),
                "Field metadata has from_bytes = 0 which must be less than to_bytes = 0");
    }

    @Test
    public void serializeRequestTest_negativeRangeMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestNegativeRangeMessage.getDefaultInstance()),
                "Field metadata has from_bytes = -1 which is less than zero");
    }

    @Test
    public void serializeRequestTest_rangeBiggerThanCountMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestRangeBiggerThanCountMessage.getDefaultInstance()),
                "Field metadata has to_bytes = 11 which is bigger than message bytes size = 10");
    }

    @Test
    public void serializeRequestTest_rangeIntersectMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestRangesIntersectMessage.getDefaultInstance()),
                "Field value bytes range [0, 4] intersects with another field metadata bytes range [2, 6]");
    }

    @Test
    public void serializeRequestTest_unsupportedTypes() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestStringValueMessage.newBuilder()
                .setMessage("Message")
                .build()), "Unsupported field type: STRING");
        assertError(() -> converter.serializeRequest(null, TestFloatValueMessage.newBuilder()
                .setWeight(85.5f)
                .build()), "Unsupported field type: FLOAT");
        assertError(() -> converter.serializeRequest(null, TestDoubleValueMessage.newBuilder()
                .setImpedance(300.5678d)
                .build()), "Unsupported field type: DOUBLE");
    }

    @Test
    public void serializeRequestTest_wrongIntegerRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestWrongIntegerRangeMessage.newBuilder()
                .setValue(20)
                .build()),
                "Integer field value has unsupported size 9. Only sizes in [1, 8] are supported.");
    }

    @Test
    public void serializeRequestTest_wrongLongRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestWrongLongRangeMessage.newBuilder()
                .setValue(100000)
                .build()),
                "Integer field value has unsupported size 9. Only sizes in [1, 8] are supported.");
    }

    @Test
    public void serializeRequestTest_wrongEnumRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestWrongEnumRangeMessage.newBuilder()
                .setType(TestEnum.VALUE_1)
                .build()),
                "Enum TestEnum field type has unsupported size 5. Only sizes in [1, 4] are supported.");
    }

    @Test
    public void serializeRequestTest_wrongBooleanRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestWrongBooleanRangeMessage.newBuilder()
                .setRelease(true)
                .build()),
                "Boolean field release has bytes size = 2, but has to be of size 1");
    }

    @Test
    public void serializeRequestTest_notEnoughRangeForEnum() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestSmallTwoBytesEnumRangeMessage.newBuilder()
                .setBigEnumValue(2)
                .build()),
                "1 byte(s) not enough for TestTwoBytesSizeEnum enum that has 2222 max number");
        assertError(() -> converter.serializeRequest(null, TestSmallThreeBytesEnumRangeMessage.newBuilder()
                .setBigEnumValue(2)
                .build()),
                "2 byte(s) not enough for TestThreeBytesSizeEnum enum that has 222222 max number");
        assertError(() -> converter.serializeRequest(null, TestSmallFourBytesEnumRangeMessage.newBuilder()
                .setBigEnumValue(2)
                .build()),
                "3 byte(s) not enough for TestFourBytesSizeEnum enum that has 222222222 max number");
    }

    @Test
    public void deserializeResponseTest() throws Exception {
        assertThat(converterLittleEndian.deserializeResponse(null, TestMessage.getDefaultInstance(), IMAGE_REQUEST_1_BYTES_LITTLE_ENDIAN))
                .isEqualTo(IMAGE_REQUEST_1);
        assertThat(converter.deserializeResponse(null, TestMessage.getDefaultInstance(), IMAGE_REQUEST_1_BYTES_BIG_ENDIAN))
                .isEqualTo(IMAGE_REQUEST_1);
        assertThat(converterLittleEndian.deserializeResponse(null, TestMessage.getDefaultInstance(), IMAGE_REQUEST_2_BYTES_LITTLE_ENDIAN))
                .isEqualTo(IMAGE_REQUEST_2);
        assertThat(converter.deserializeResponse(null, TestMessage.getDefaultInstance(), IMAGE_REQUEST_2_BYTES_BIG_ENDIAN))
                .isEqualTo(IMAGE_REQUEST_2);
        assertThat(converterLittleEndian.deserializeResponse(null, TestIntegerMessage.getDefaultInstance(),
                TEST_OPTIONS_INTEGER_BYTES_LITTLE_ENDIAN))
                .isEqualTo(TEST_OPTIONS_INTEGER);
        assertThat(converter.deserializeResponse(null, TestIntegerMessage.getDefaultInstance(),
                TEST_OPTIONS_INTEGER_BYTES_BIG_ENDIAN))
                .isEqualTo(TEST_OPTIONS_INTEGER);
        assertThat(converterLittleEndian.deserializeResponse(null, TestLongMessage.getDefaultInstance(),
                TEST_OPTIONS_LONG_BYTES_LITTLE_ENDIAN))
                .isEqualTo(TEST_OPTIONS_LONG);
        assertThat(converter.deserializeResponse(null, TestLongMessage.getDefaultInstance(),
                TEST_OPTIONS_LONG_BYTES_BIG_ENDIAN))
                .isEqualTo(TEST_OPTIONS_LONG);
        assertThat(converterLittleEndian.deserializeResponse(null, TestBoolMessage.getDefaultInstance(),
                TEST_OPTIONS_BOOL_BYTES))
                .isEqualTo(TEST_OPTIONS_BOOL);
        assertThat(converter.deserializeResponse(null, TestBoolMessage.getDefaultInstance(),
                TEST_OPTIONS_BOOL_BYTES))
                .isEqualTo(TEST_OPTIONS_BOOL);
        assertThat(converterLittleEndian.deserializeResponse(null, TestEnumMessage.getDefaultInstance(),
                TEST_OPTIONS_ENUM_BYTES))
                .isEqualTo(TEST_OPTIONS_ENUM);
        assertThat(converter.deserializeResponse(null, TestEnumMessage.getDefaultInstance(),
                TEST_OPTIONS_ENUM_BYTES))
                .isEqualTo(TEST_OPTIONS_ENUM);
        assertThat(converterLittleEndian.deserializeResponse(null, TestNonPrimitiveMessage.getDefaultInstance(),
                TEST_OPTIONS_MESSAGE_BYTES_LITTLE_ENDIAN))
                .isEqualTo(TEST_NON_PRIMITIVE_MESSAGE);
        assertThat(converter.deserializeResponse(null, TestNonPrimitiveMessage.getDefaultInstance(),
                TEST_OPTIONS_MESSAGE_BYTES_BIG_ENDIAN))
                .isEqualTo(TEST_NON_PRIMITIVE_MESSAGE);
    }

    @Test
    public void deserializeResponseTest_emptyMessage() throws Exception {
        assertThat(converter.deserializeResponse(null, TestEmptyMessage.getDefaultInstance(), new byte[0]))
                .isEqualTo(TestEmptyMessage.getDefaultInstance());
    }

    @Test
    public void deserializeResponseTest_wrongMessageByteSize() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestMessage.getDefaultInstance(), new byte[10]),
                "Declared size 22 of message TestMessage is not equal to device response size 10");
    }

    @Test
    public void deserializeResponseTest_messageWithFieldAndWithoutByteSize() throws Exception {
        assertError(() -> converter.deserializeResponse(null,
                TestNoBytesSizeMessage.getDefaultInstance(),
                IMAGE_REQUEST_1_BYTES_LITTLE_ENDIAN),
                "A non empty message TestNoBytesSizeMessage doesn't have com.blerpc.message_size annotation.");
    }

    @Test
    public void deserializeResponseTest_noByteRangeMessage() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestNoBytesRangeMessage.getDefaultInstance(), new byte[2]),
                "Proto field value doesn't have com.blerpc.field_bytes annotation");
    }

    @Test
    public void deserializeResponseTest_zeroSizeRangeMessage() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestZeroSizeRangeMessage.getDefaultInstance(), new byte[10]),
                "Field metadata has from_bytes = 0 which must be less than to_bytes = 0");
    }

    @Test
    public void deserializeResponseTest_negativeRangeMessage() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestNegativeRangeMessage.getDefaultInstance(), new byte[10]),
                "Field metadata has from_bytes = -1 which is less than zero");
    }

    @Test
    public void deserializeResponseTest_rangeBiggerThanCountMessage() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestRangeBiggerThanCountMessage.getDefaultInstance(), new byte[10]),
                "Field metadata has to_bytes = 11 which is bigger than message bytes size = 10");
    }

    @Test
    public void deserializeResponseTest_unsupportedTypes() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestStringValueMessage.getDefaultInstance(), new byte[4]),
                "Unsupported field type: STRING");
        assertError(() -> converter.deserializeResponse(null, TestFloatValueMessage.getDefaultInstance(), new byte[4]),
                "Unsupported field type: FLOAT");
        assertError(() -> converter.deserializeResponse(null, TestDoubleValueMessage.getDefaultInstance(), new byte[4]),
                "Unsupported field type: DOUBLE");
    }

    @Test
    public void deserializeResponseTest_wrongIntegerRange() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestWrongIntegerRangeMessage.getDefaultInstance(), new byte[9]),
                "Integer field value has unsupported size 9. Only sizes in [1, 8] are supported.");
    }

    @Test
    public void deserializeResponseTest_wrongLongRange() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestWrongLongRangeMessage.getDefaultInstance(), new byte[9]),
                "Integer field value has unsupported size 9. Only sizes in [1, 8] are supported.");
    }

    @Test
    public void deserializeResponseTest_wrongEnumRange() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestWrongEnumRangeMessage.getDefaultInstance(), new byte[5]),
                "Enum TestEnum field type has unsupported size 5. Only sizes in [1, 4] are supported.");
    }

    @Test
    public void deserializeResponseTest_wrongBooleanRange() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestWrongBooleanRangeMessage.getDefaultInstance(), new byte[2]),
                "Boolean field release has bytes size = 2, but has to be of size 1");
    }
}
