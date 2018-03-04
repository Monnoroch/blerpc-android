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
import com.blerpc.device.test.proto.TestFromByteBiggerToByteMessage;
import com.blerpc.device.test.proto.TestIntegerMessage;
import com.blerpc.device.test.proto.TestLongMessage;
import com.blerpc.device.test.proto.TestNegativeRangeMessage;
import com.blerpc.device.test.proto.TestNoBytesRangeMessage;
import com.blerpc.device.test.proto.TestNoBytesSizeMessage;
import com.blerpc.device.test.proto.TestRangeBiggerThanCountMessage;
import com.blerpc.device.test.proto.TestRangesIntersectMessage;
import com.blerpc.device.test.proto.TestSkippedBytesMessage;
import com.blerpc.device.test.proto.TestSmallFourBytesEnumRangeMessage;
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

    private static final TestIntegerMessage TEST_INTEGER_MESSAGE = TestIntegerMessage.newBuilder()
            .setValue(100000)
            .build();
    private static final TestLongMessage TEST_LONG_MESSAGE = TestLongMessage.newBuilder()
            .setValue(10000000000L)
            .build();
    private static final TestBoolMessage TEST_BOOL_MESSAGE = TestBoolMessage.newBuilder()
            .setValue(true)
            .build();
    private static final TestEnumMessage TEST_ENUM_MESSAGE = TestEnumMessage.newBuilder()
            .setTestEnum(TestEnum.VALUE_2)
            .build();
    private static final TestEmbeddedMessage TEST_EMBEDDED_MESSAGE = TestEmbeddedMessage.newBuilder()
            .setValue(1000)
            .setEmbeddedMessage(TEST_INTEGER_MESSAGE)
            .build();
    private static final TestSkippedBytesMessage TEST_SKIPPED_BYTES_MESSAGE = TestSkippedBytesMessage.newBuilder()
            .setValue1(1000)
            .setValue2(2000)
            .build();
    private static final byte[] TEST_INTEGER_MESSAGE_BYTES_LITTLE_ENDIAN = new byte[]{-96, -122, 1, 0};
    private static final byte[] TEST_INTEGER_MESSAGE_BYTES_BIG_ENDIAN = new byte[]{0, 1, -122, -96};
    private static final byte[] TEST_LONG_MESSAGE_BYTES_LITTLE_ENDIAN = new byte[]{0, -28, 11, 84, 2, 0, 0, 0};
    private static final byte[] TEST_LONG_MESSAGE_BYTES_BIG_ENDIAN = new byte[]{0, 0, 0, 2, 84, 11, -28, 0};
    private static final byte[] TEST_BOOL_MESSAGE_BYTES = new byte[]{1};
    private static final byte[] TEST_ENUM_MESSAGE_BYTES = new byte[]{2};
    private static final byte[] TEST_EMBEDDED_MESSAGE_BYTES_LITTLE_ENDIAN = new byte[]{-24, 3, 0, 0, -96, -122, 1, 0};
    private static final byte[] TEST_EMBEDDED_MESSAGE_BYTES_BIG_ENDIAN = new byte[]{0, 0, 3, -24, 0, 1, -122, -96};
    private static final byte[] TEST_SKIPPED_BYTES_MESSAGE_LITTLE_ENDIAN = new byte[]{-24, 3, 0, 0, 0, 0, -48, 7, 0, 0};
    private static final byte[] TEST_SKIPPED_BYTES_MESSAGE_BIG_ENDIAN = new byte[]{0, 0, 3, -24, 0, 0, 0, 0, 7, -48};

    AnnotationMessageConverter converter = new AnnotationMessageConverter();
    AnnotationMessageConverter converterLittleEndian = new AnnotationMessageConverter(ByteOrder.LITTLE_ENDIAN);

    @Test
    public void serializeRequestTest_primitiveVariables() throws Exception {
        assertThat(converterLittleEndian.serializeRequest(null, TEST_INTEGER_MESSAGE)).isEqualTo(TEST_INTEGER_MESSAGE_BYTES_LITTLE_ENDIAN);
        assertThat(converterLittleEndian.serializeRequest(null, TEST_LONG_MESSAGE)).isEqualTo(TEST_LONG_MESSAGE_BYTES_LITTLE_ENDIAN);
        assertThat(converterLittleEndian.serializeRequest(null, TEST_BOOL_MESSAGE)).isEqualTo(TEST_BOOL_MESSAGE_BYTES);
        assertThat(converterLittleEndian.serializeRequest(null, TEST_ENUM_MESSAGE)).isEqualTo(TEST_ENUM_MESSAGE_BYTES);
        assertThat(converter.serializeRequest(null, TEST_INTEGER_MESSAGE)).isEqualTo(TEST_INTEGER_MESSAGE_BYTES_BIG_ENDIAN);
        assertThat(converter.serializeRequest(null, TEST_LONG_MESSAGE)).isEqualTo(TEST_LONG_MESSAGE_BYTES_BIG_ENDIAN);
        assertThat(converter.serializeRequest(null, TEST_BOOL_MESSAGE)).isEqualTo(TEST_BOOL_MESSAGE_BYTES);
        assertThat(converter.serializeRequest(null, TEST_ENUM_MESSAGE)).isEqualTo(TEST_ENUM_MESSAGE_BYTES);
    }

    @Test
    public void serializeRequestTest_embeddedMessage() throws Exception {
        assertThat(converterLittleEndian.serializeRequest(null, TEST_EMBEDDED_MESSAGE)).isEqualTo(TEST_EMBEDDED_MESSAGE_BYTES_LITTLE_ENDIAN);
        assertThat(converter.serializeRequest(null, TEST_EMBEDDED_MESSAGE)).isEqualTo(TEST_EMBEDDED_MESSAGE_BYTES_BIG_ENDIAN);
    }

    @Test
    public void serializeRequestTest_skippedBytes() throws Exception {
        assertThat(converterLittleEndian.serializeRequest(null, TEST_SKIPPED_BYTES_MESSAGE))
                .isEqualTo(TEST_SKIPPED_BYTES_MESSAGE_LITTLE_ENDIAN);
        assertThat(converter.serializeRequest(null, TEST_SKIPPED_BYTES_MESSAGE))
                .isEqualTo(TEST_SKIPPED_BYTES_MESSAGE_BIG_ENDIAN);
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
                "Field value has from_bytes = 0 which must be less than to_bytes = 0");
    }

    @Test
    public void serializeRequestTest_fromByteBiggerToByteMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestFromByteBiggerToByteMessage.getDefaultInstance()),
                "Field value has from_bytes = 1 which must be less than to_bytes = 0");
    }

    @Test
    public void serializeRequestTest_negativeRangeMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestNegativeRangeMessage.getDefaultInstance()),
                "Field value has from_bytes = -1 which is less than zero");
    }

    @Test
    public void serializeRequestTest_rangeBiggerThanCountMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestRangeBiggerThanCountMessage.getDefaultInstance()),
                "Field value has to_bytes = 11 which is bigger than message bytes size = 10");
    }

    @Test
    public void serializeRequestTest_rangeIntersectMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestRangesIntersectMessage.getDefaultInstance()),
                "Field value_1 bytes range [0, 4] intersects with another field value_2 bytes range [2, 10]");
    }

    @Test
    public void serializeRequestTest_unsupportedTypes() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestStringValueMessage.newBuilder()
                .setValue("Message")
                .build()), "Unsupported field type: STRING");
        assertError(() -> converter.serializeRequest(null, TestFloatValueMessage.newBuilder()
                .setValue(85.5f)
                .build()), "Unsupported field type: FLOAT");
        assertError(() -> converter.serializeRequest(null, TestDoubleValueMessage.newBuilder()
                .setValue(300.5678d)
                .build()), "Unsupported field type: DOUBLE");
    }

    @Test
    public void serializeRequestTest_wrongIntegerRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestWrongIntegerRangeMessage.newBuilder()
                .setValue(20)
                .build()),
                "Int32 field value has unsupported size 5. Only sizes in [1, 4] are supported.");
    }

    @Test
    public void serializeRequestTest_wrongLongRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestWrongLongRangeMessage.newBuilder()
                .setValue(100000)
                .build()),
                "Int64 field value has unsupported size 9. Only sizes in [1, 8] are supported.");
    }

    @Test
    public void serializeRequestTest_wrongEnumRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestWrongEnumRangeMessage.newBuilder()
                .setTestEnum(TestEnum.VALUE_1)
                .build()),
                "Enum TestEnum field test_enum has unsupported size 5. Only sizes in [1, 4] are supported.");
    }

    @Test
    public void serializeRequestTest_wrongBooleanRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestWrongBooleanRangeMessage.newBuilder()
                .setValue(true)
                .build()),
                "Boolean field value has bytes size = 2, but has to be of size 1");
    }

    @Test
    public void serializeRequestTest_notEnoughRangeForEnum() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestSmallFourBytesEnumRangeMessage.newBuilder()
                .setBigEnumValue(2)
                .build()),
                "3 byte(s) not enough for TestFourBytesEnum enum that has 222222222 max number");
    }

    @Test
    public void deserializeResponseTest_primitiveVariables() throws Exception {
        assertThat(converterLittleEndian.deserializeResponse(null, TestIntegerMessage.getDefaultInstance(),
                TEST_INTEGER_MESSAGE_BYTES_LITTLE_ENDIAN))
                .isEqualTo(TEST_INTEGER_MESSAGE);
        assertThat(converter.deserializeResponse(null, TestIntegerMessage.getDefaultInstance(),
                TEST_INTEGER_MESSAGE_BYTES_BIG_ENDIAN))
                .isEqualTo(TEST_INTEGER_MESSAGE);
        assertThat(converterLittleEndian.deserializeResponse(null, TestLongMessage.getDefaultInstance(),
                TEST_LONG_MESSAGE_BYTES_LITTLE_ENDIAN))
                .isEqualTo(TEST_LONG_MESSAGE);
        assertThat(converter.deserializeResponse(null, TestLongMessage.getDefaultInstance(),
                TEST_LONG_MESSAGE_BYTES_BIG_ENDIAN))
                .isEqualTo(TEST_LONG_MESSAGE);
        assertThat(converterLittleEndian.deserializeResponse(null, TestBoolMessage.getDefaultInstance(),
                TEST_BOOL_MESSAGE_BYTES))
                .isEqualTo(TEST_BOOL_MESSAGE);
        assertThat(converter.deserializeResponse(null, TestBoolMessage.getDefaultInstance(),
                TEST_BOOL_MESSAGE_BYTES))
                .isEqualTo(TEST_BOOL_MESSAGE);
        assertThat(converterLittleEndian.deserializeResponse(null, TestEnumMessage.getDefaultInstance(),
                TEST_ENUM_MESSAGE_BYTES))
                .isEqualTo(TEST_ENUM_MESSAGE);
        assertThat(converter.deserializeResponse(null, TestEnumMessage.getDefaultInstance(),
                TEST_ENUM_MESSAGE_BYTES))
                .isEqualTo(TEST_ENUM_MESSAGE);
    }

    @Test
    public void deserializeResponseTest_embeddedMessage() throws Exception {
        assertThat(converterLittleEndian.deserializeResponse(null, TestEmbeddedMessage.getDefaultInstance(),
                TEST_EMBEDDED_MESSAGE_BYTES_LITTLE_ENDIAN))
                .isEqualTo(TEST_EMBEDDED_MESSAGE);
        assertThat(converter.deserializeResponse(null, TestEmbeddedMessage.getDefaultInstance(),
                TEST_EMBEDDED_MESSAGE_BYTES_BIG_ENDIAN))
                .isEqualTo(TEST_EMBEDDED_MESSAGE);
    }

    @Test
    public void deserializeResponseTest_skippedBytes() throws Exception {
        assertThat(converterLittleEndian.deserializeResponse(null, TestSkippedBytesMessage.getDefaultInstance(),
                TEST_SKIPPED_BYTES_MESSAGE_LITTLE_ENDIAN))
                .isEqualTo(TEST_SKIPPED_BYTES_MESSAGE);
        assertThat(converter.deserializeResponse(null, TestSkippedBytesMessage.getDefaultInstance(),
                TEST_SKIPPED_BYTES_MESSAGE_BIG_ENDIAN))
                .isEqualTo(TEST_SKIPPED_BYTES_MESSAGE);
    }

    @Test
    public void deserializeResponseTest_emptyMessage() throws Exception {
        assertThat(converter.deserializeResponse(null, TestEmptyMessage.getDefaultInstance(), new byte[0]))
                .isEqualTo(TestEmptyMessage.getDefaultInstance());
    }

    @Test
    public void deserializeResponseTest_wrongMessageByteSize() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestIntegerMessage.getDefaultInstance(), new byte[10]),
                "Declared size 4 of message TestIntegerMessage is not equal to device response size 10");
    }

    @Test
    public void deserializeResponseTest_messageWithFieldAndWithoutByteSize() throws Exception {
        assertError(() -> converter.deserializeResponse(null,
                TestNoBytesSizeMessage.getDefaultInstance(),
                TEST_BOOL_MESSAGE_BYTES),
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
                "Field value has from_bytes = 0 which must be less than to_bytes = 0");
    }

    @Test
    public void deserializeResponseTest_negativeRangeMessage() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestNegativeRangeMessage.getDefaultInstance(), new byte[10]),
                "Field value has from_bytes = -1 which is less than zero");
    }

    @Test
    public void deserializeResponseTest_rangeBiggerThanCountMessage() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestRangeBiggerThanCountMessage.getDefaultInstance(), new byte[10]),
                "Field value has to_bytes = 11 which is bigger than message bytes size = 10");
    }

    @Test
    public void deserializeResponseTest_unsupportedTypes() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestStringValueMessage.getDefaultInstance(), new byte[4]),
                "Unsupported field type: STRING");
        assertError(() -> converter.deserializeResponse(null, TestFloatValueMessage.getDefaultInstance(), new byte[4]),
                "Unsupported field type: FLOAT");
        assertError(() -> converter.deserializeResponse(null, TestDoubleValueMessage.getDefaultInstance(), new byte[8]),
                "Unsupported field type: DOUBLE");
    }

    @Test
    public void deserializeResponseTest_wrongIntegerRange() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestWrongIntegerRangeMessage.getDefaultInstance(), new byte[5]),
                "Int32 field value has unsupported size 5. Only sizes in [1, 4] are supported.");
    }

    @Test
    public void deserializeResponseTest_wrongLongRange() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestWrongLongRangeMessage.getDefaultInstance(), new byte[9]),
                "Int64 field value has unsupported size 9. Only sizes in [1, 8] are supported.");
    }

    @Test
    public void deserializeResponseTest_wrongEnumRange() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestWrongEnumRangeMessage.getDefaultInstance(), new byte[5]),
                "Enum TestEnum field test_enum has unsupported size 5. Only sizes in [1, 4] are supported.");
    }

    @Test
    public void deserializeResponseTest_wrongBooleanRange() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestWrongBooleanRangeMessage.getDefaultInstance(), new byte[2]),
                "Boolean field value has bytes size = 2, but has to be of size 1");
    }
}
