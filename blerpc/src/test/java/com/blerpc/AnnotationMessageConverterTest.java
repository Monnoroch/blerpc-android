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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link AnnotationMessageConverter}.
 */
@RunWith(MockitoJUnitRunner.class)
public class AnnotationMessageConverterTest {

    private static final byte[] TEST_INT_BYTE_ARRAY = new byte[]{0, 1, -122, -96};
    private static final byte[] TEST_LONG_BYTE_ARRAY = new byte[]{0, 0, 0, 2, 84, 11, -28, 0};
    private static final byte[] TEST_BOOL_BYTE_ARRAY = new byte[]{1};
    private static final byte[] TEST_ENUM_BYTE_ARRAY = new byte[]{0, 2};
    private static final byte[] TEST_LITTLE_ENDIAN_ENUM_BYTE_ARRAY = new byte[]{2, 0};

    AnnotationMessageConverter converter = new AnnotationMessageConverter();
    AnnotationMessageConverter converterLittleEndian = new AnnotationMessageConverter(ByteOrder.LITTLE_ENDIAN);

    @Test
    public void serializeRequestTest_primitiveVariables() throws Exception {
        assertThat(converter.serializeRequest(null, TestIntegerMessage.newBuilder()
                .setIntValue(intFrom(TEST_INT_BYTE_ARRAY))
                .build()))
                .isEqualTo(TEST_INT_BYTE_ARRAY);
        assertThat(converterLittleEndian.serializeRequest(null, TestIntegerMessage.newBuilder()
                .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
                .build()))
                .isEqualTo(TEST_INT_BYTE_ARRAY);
        assertThat(converter.serializeRequest(null, TestLongMessage.newBuilder()
                .setLongValue(longFrom(TEST_LONG_BYTE_ARRAY))
                .build()))
                .isEqualTo(TEST_LONG_BYTE_ARRAY);
        assertThat(converterLittleEndian.serializeRequest(null, TestLongMessage.newBuilder()
                .setLongValue(littleEndianLongFrom(TEST_LONG_BYTE_ARRAY))
                .build()))
                .isEqualTo(TEST_LONG_BYTE_ARRAY);
        assertThat(converter.serializeRequest(null, TestEnumMessage.newBuilder()
                .setEnumValue(TestEnum.forNumber(shortFrom(TEST_ENUM_BYTE_ARRAY)))
                .build()))
                .isEqualTo(TEST_ENUM_BYTE_ARRAY);
        assertThat(converterLittleEndian.serializeRequest(null, TestEnumMessage.newBuilder()
                .setEnumValue(TestEnum.forNumber(littleEndianShortFrom(TEST_LITTLE_ENDIAN_ENUM_BYTE_ARRAY)))
                .build()))
                .isEqualTo(TEST_LITTLE_ENDIAN_ENUM_BYTE_ARRAY);
        assertThat(converter.serializeRequest(null, TestBoolMessage.newBuilder()
                .setBoolValue(booleanFrom(TEST_BOOL_BYTE_ARRAY))
                .build()))
                .isEqualTo(TEST_BOOL_BYTE_ARRAY);
        assertThat(converterLittleEndian.serializeRequest(null, TestBoolMessage.newBuilder()
                .setBoolValue(booleanFrom(TEST_BOOL_BYTE_ARRAY))
                .build()))
                .isEqualTo(TEST_BOOL_BYTE_ARRAY);
    }

    @Test
    public void serializeRequestTest_embeddedMessage() throws Exception {
        assertThat(converter.serializeRequest(null, TestEmbeddedMessage.newBuilder()
                .setIntValue(intFrom(TEST_INT_BYTE_ARRAY))
                .setEmbeddedMessage(TestIntegerMessage.newBuilder()
                        .setIntValue(intFrom(TEST_INT_BYTE_ARRAY)))
                .build()))
                .isEqualTo(concatArrays(TEST_INT_BYTE_ARRAY, TEST_INT_BYTE_ARRAY));
        assertThat(converterLittleEndian.serializeRequest(null, TestEmbeddedMessage.newBuilder()
                .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
                .setEmbeddedMessage(TestIntegerMessage.newBuilder()
                        .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY)))
                .build()))
                .isEqualTo(concatArrays(TEST_INT_BYTE_ARRAY, TEST_INT_BYTE_ARRAY));
    }

    @Test
    public void serializeRequestTest_skippedBytes() throws Exception {
        assertThat(converter.serializeRequest(null, TestSkippedBytesMessage.newBuilder()
                .setIntValue1(intFrom(TEST_INT_BYTE_ARRAY))
                .setIntValue2(intFrom(TEST_INT_BYTE_ARRAY))
                .build()))
                .isEqualTo(concatArrays(TEST_INT_BYTE_ARRAY, new byte[]{0, 0}, TEST_INT_BYTE_ARRAY));

        assertThat(converterLittleEndian.serializeRequest(null, TestSkippedBytesMessage.newBuilder()
                .setIntValue1(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
                .setIntValue2(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
                .build()))
                .isEqualTo(concatArrays(TEST_INT_BYTE_ARRAY,  new byte[]{0, 0}, TEST_INT_BYTE_ARRAY));
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
                "Proto field int_value doesn't have com.blerpc.field_bytes annotation");
    }

    @Test
    public void serializeRequestTest_zeroSizeRangeMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestZeroSizeRangeMessage.getDefaultInstance()),
                "Field int_value has from_bytes = 0 which must be less than to_bytes = 0");
    }

    @Test
    public void serializeRequestTest_fromByteBiggerToByteMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestFromByteBiggerToByteMessage.getDefaultInstance()),
                "Field int_value has from_bytes = 1 which must be less than to_bytes = 0");
    }

    @Test
    public void serializeRequestTest_negativeRangeMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestNegativeRangeMessage.getDefaultInstance()),
                "Field int_value has from_bytes = -1 which is less than zero");
    }

    @Test
    public void serializeRequestTest_rangeBiggerThanCountMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestRangeBiggerThanCountMessage.getDefaultInstance()),
                "Field int_value has to_bytes = 11 which is bigger than message bytes size = 10");
    }

    @Test
    public void serializeRequestTest_rangeIntersectMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestRangesIntersectMessage.getDefaultInstance()),
                "Field int_value_1 bytes range [0, 4] intersects with another field int_value_2 bytes range [2, 10]");
    }

    @Test
    public void serializeRequestTest_unsupportedTypes() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestStringValueMessage.newBuilder()
                .setStringValue("Message")
                .build()), "Unsupported field type: STRING");
        assertError(() -> converter.serializeRequest(null, TestFloatValueMessage.newBuilder()
                .setFloatValue(85.5f)
                .build()), "Unsupported field type: FLOAT");
        assertError(() -> converter.serializeRequest(null, TestDoubleValueMessage.newBuilder()
                .setDoubleValue(300.5678d)
                .build()), "Unsupported field type: DOUBLE");
    }

    @Test
    public void serializeRequestTest_wrongIntegerRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestWrongIntegerRangeMessage.newBuilder()
                .setIntValue(20)
                .build()),
                "Int32 field int_value has unsupported size 5. Only sizes in [1, 4] are supported.");
    }

    @Test
    public void serializeRequestTest_wrongLongRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestWrongLongRangeMessage.newBuilder()
                .setLongValue(100000)
                .build()),
                "Int64 field long_value has unsupported size 9. Only sizes in [1, 8] are supported.");
    }

    @Test
    public void serializeRequestTest_wrongEnumRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestWrongEnumRangeMessage.newBuilder()
                .setEnumValue(TestEnum.VALUE_1)
                .build()),
                "Enum TestEnum field enum_value has unsupported size 5. Only sizes in [1, 4] are supported.");
    }

    @Test
    public void serializeRequestTest_wrongBooleanRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestWrongBooleanRangeMessage.newBuilder()
                .setBoolValue(true)
                .build()),
                "Boolean field bool_value has bytes size = 2, but has to be of size 1");
    }

    @Test
    public void serializeRequestTest_notEnoughRangeForEnum() throws Exception {
        assertError(() -> converter.serializeRequest(null, TestSmallFourBytesEnumRangeMessage.newBuilder()
                .setEnumValueValue(2)
                .build()),
                "3 byte(s) not enough for TestFourBytesEnum enum that has 222222222 max number");
    }

    @Test
    public void deserializeResponseTest_primitiveVariables() throws Exception {
        assertThat(converter.deserializeResponse(null, TestIntegerMessage.getDefaultInstance(),
                TEST_INT_BYTE_ARRAY))
                .isEqualTo(TestIntegerMessage.newBuilder()
                        .setIntValue(intFrom(TEST_INT_BYTE_ARRAY))
                        .build());
        assertThat(converterLittleEndian.deserializeResponse(null, TestIntegerMessage.getDefaultInstance(),
                TEST_INT_BYTE_ARRAY))
                .isEqualTo(TestIntegerMessage.newBuilder()
                        .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
                        .build());
        assertThat(converter.deserializeResponse(null, TestLongMessage.getDefaultInstance(),
                TEST_LONG_BYTE_ARRAY))
                .isEqualTo(TestLongMessage.newBuilder()
                        .setLongValue(longFrom(TEST_LONG_BYTE_ARRAY))
                        .build());
        assertThat(converterLittleEndian.deserializeResponse(null, TestLongMessage.getDefaultInstance(),
                TEST_LONG_BYTE_ARRAY))
                .isEqualTo(TestLongMessage.newBuilder()
                        .setLongValue(littleEndianLongFrom(TEST_LONG_BYTE_ARRAY))
                        .build());
        assertThat(converter.deserializeResponse(null, TestEnumMessage.getDefaultInstance(),
                TEST_ENUM_BYTE_ARRAY))
                .isEqualTo(TestEnumMessage.newBuilder()
                        .setEnumValue(TestEnum.forNumber(shortFrom(TEST_ENUM_BYTE_ARRAY)))
                        .build());
        assertThat(converterLittleEndian.deserializeResponse(null, TestEnumMessage.getDefaultInstance(),
                TEST_LITTLE_ENDIAN_ENUM_BYTE_ARRAY))
                .isEqualTo(TestEnumMessage.newBuilder()
                        .setEnumValue(TestEnum.forNumber(littleEndianShortFrom(TEST_LITTLE_ENDIAN_ENUM_BYTE_ARRAY)))
                        .build());
        assertThat(converter.deserializeResponse(null, TestBoolMessage.getDefaultInstance(),
                TEST_BOOL_BYTE_ARRAY))
                .isEqualTo(TestBoolMessage.newBuilder()
                        .setBoolValue(booleanFrom(TEST_BOOL_BYTE_ARRAY))
                        .build());
        assertThat(converterLittleEndian.deserializeResponse(null, TestBoolMessage.getDefaultInstance(),
                TEST_BOOL_BYTE_ARRAY))
                .isEqualTo(TestBoolMessage.newBuilder()
                        .setBoolValue(booleanFrom(TEST_BOOL_BYTE_ARRAY))
                        .build());
    }

    @Test
    public void deserializeResponseTest_embeddedMessage() throws Exception {
        assertThat(converter.deserializeResponse(null, TestEmbeddedMessage.getDefaultInstance(),
                concatArrays(TEST_INT_BYTE_ARRAY, TEST_INT_BYTE_ARRAY)))
                .isEqualTo(TestEmbeddedMessage.newBuilder()
                        .setIntValue(intFrom(TEST_INT_BYTE_ARRAY))
                        .setEmbeddedMessage(TestIntegerMessage.newBuilder()
                                .setIntValue(intFrom(TEST_INT_BYTE_ARRAY)))
                        .build());
        assertThat(converterLittleEndian.deserializeResponse(null, TestEmbeddedMessage.getDefaultInstance(),
                concatArrays(TEST_INT_BYTE_ARRAY, TEST_INT_BYTE_ARRAY)))
                .isEqualTo(TestEmbeddedMessage.newBuilder()
                        .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
                        .setEmbeddedMessage(TestIntegerMessage.newBuilder()
                                .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY)))
                        .build());
    }

    @Test
    public void deserializeResponseTest_skippedBytes() throws Exception {
        assertThat(converter.deserializeResponse(null, TestSkippedBytesMessage.getDefaultInstance(),
                concatArrays(TEST_INT_BYTE_ARRAY, new byte[]{0, 0}, TEST_INT_BYTE_ARRAY)))
                .isEqualTo(TestSkippedBytesMessage.newBuilder()
                        .setIntValue1(intFrom(TEST_INT_BYTE_ARRAY))
                        .setIntValue2(intFrom(TEST_INT_BYTE_ARRAY))
                        .build());
        assertThat(converterLittleEndian.deserializeResponse(null, TestSkippedBytesMessage.getDefaultInstance(),
                concatArrays(TEST_INT_BYTE_ARRAY, new byte[]{0, 0}, TEST_INT_BYTE_ARRAY)))
                .isEqualTo(TestSkippedBytesMessage.newBuilder()
                        .setIntValue1(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
                        .setIntValue2(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
                        .build());
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
                TEST_BOOL_BYTE_ARRAY),
                "A non empty message TestNoBytesSizeMessage doesn't have com.blerpc.message_size annotation.");
    }

    @Test
    public void deserializeResponseTest_noByteRangeMessage() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestNoBytesRangeMessage.getDefaultInstance(), new byte[2]),
                "Proto field int_value doesn't have com.blerpc.field_bytes annotation");
    }

    @Test
    public void deserializeResponseTest_zeroSizeRangeMessage() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestZeroSizeRangeMessage.getDefaultInstance(), new byte[10]),
                "Field int_value has from_bytes = 0 which must be less than to_bytes = 0");
    }

    @Test
    public void deserializeResponseTest_negativeRangeMessage() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestNegativeRangeMessage.getDefaultInstance(), new byte[10]),
                "Field int_value has from_bytes = -1 which is less than zero");
    }

    @Test
    public void deserializeResponseTest_rangeBiggerThanCountMessage() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestRangeBiggerThanCountMessage.getDefaultInstance(), new byte[10]),
                "Field int_value has to_bytes = 11 which is bigger than message bytes size = 10");
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
                "Int32 field int_value has unsupported size 5. Only sizes in [1, 4] are supported.");
    }

    @Test
    public void deserializeResponseTest_wrongLongRange() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestWrongLongRangeMessage.getDefaultInstance(), new byte[9]),
                "Int64 field long_value has unsupported size 9. Only sizes in [1, 8] are supported.");
    }

    @Test
    public void deserializeResponseTest_wrongEnumRange() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestWrongEnumRangeMessage.getDefaultInstance(), new byte[5]),
                "Enum TestEnum field enum_value has unsupported size 5. Only sizes in [1, 4] are supported.");
    }

    @Test
    public void deserializeResponseTest_wrongBooleanRange() throws Exception {
        assertError(() -> converter.deserializeResponse(null, TestWrongBooleanRangeMessage.getDefaultInstance(), new byte[2]),
                "Boolean field bool_value has bytes size = 2, but has to be of size 1");
    }

    private static short shortFrom(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getShort();
    }

    private static short littleEndianShortFrom(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    private static int intFrom(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    private static int littleEndianIntFrom(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static long longFrom(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getLong();
    }

    private static long littleEndianLongFrom(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    private static boolean booleanFrom(byte[] bytes) {
        return bytes[0] != 0;
    }

    private static byte[] concatArrays(byte[] firstArray, byte[]... arrays) {
        int totalLength = firstArray.length;
        for (byte[] array : arrays) {
            totalLength += array.length;
        }
        byte[] result = Arrays.copyOf(firstArray, totalLength);
        int offset = firstArray.length;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }
}
