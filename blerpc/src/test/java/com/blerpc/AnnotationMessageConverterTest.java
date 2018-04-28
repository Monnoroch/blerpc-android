package com.blerpc;

import static com.blerpc.Assert.assertError;
import static com.google.common.truth.Truth.assertThat;

import com.blerpc.device.test.proto.TestBigEndianMessage;
import com.blerpc.device.test.proto.TestBigValueEnum;
import com.blerpc.device.test.proto.TestBoolMessage;
import com.blerpc.device.test.proto.TestByteStringMessage;
import com.blerpc.device.test.proto.TestDoubleValueMessage;
import com.blerpc.device.test.proto.TestEmbeddedMessageNotContainsOrder;
import com.blerpc.device.test.proto.TestEmbeddedMessageOrderOverrideMessage;
import com.blerpc.device.test.proto.TestEmptyMessage;
import com.blerpc.device.test.proto.TestEnum;
import com.blerpc.device.test.proto.TestEnumMessage;
import com.blerpc.device.test.proto.TestFieldOrderOverrideMessage;
import com.blerpc.device.test.proto.TestFloatValueMessage;
import com.blerpc.device.test.proto.TestIntegerMessage;
import com.blerpc.device.test.proto.TestLittleEndianMessage;
import com.blerpc.device.test.proto.TestLongMessage;
import com.blerpc.device.test.proto.TestMessageWithGaps;
import com.blerpc.device.test.proto.TestNegativeRangeFromMessage;
import com.blerpc.device.test.proto.TestNegativeSizeRangeMessage;
import com.blerpc.device.test.proto.TestNoBytesRangeMessage;
import com.blerpc.device.test.proto.TestNoBytesSizeMessage;
import com.blerpc.device.test.proto.TestNonPrimitiveFieldMessage;
import com.blerpc.device.test.proto.TestRangeBiggerThanCountMessage;
import com.blerpc.device.test.proto.TestRangesIntersectMessage;
import com.blerpc.device.test.proto.TestSevenBytesLongMessage;
import com.blerpc.device.test.proto.TestSmallEnumRangeMessage;
import com.blerpc.device.test.proto.TestStringValueMessage;
import com.blerpc.device.test.proto.TestThreeBytesEnumMessage;
import com.blerpc.device.test.proto.TestThreeBytesIntegerMessage;
import com.blerpc.device.test.proto.TestWrongBooleanRangeMessage;
import com.blerpc.device.test.proto.TestWrongEnumRangeMessage;
import com.blerpc.device.test.proto.TestWrongIntegerRangeMessage;
import com.blerpc.device.test.proto.TestWrongLongRangeMessage;
import com.blerpc.device.test.proto.TestZeroBytesMessage;
import com.blerpc.device.test.proto.TestZeroSizeRangeMessage;
import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link AnnotationMessageConverter}.
 */
@RunWith(MockitoJUnitRunner.class)
public class AnnotationMessageConverterTest {

  private static final byte[] TEST_INT_BYTE_ARRAY = new byte[]{15, 1, -122, -96};
  private static final byte[] TEST_LONG_BYTE_ARRAY = new byte[]{2, 6, 8, 2, 84, 11, -28, 0};
  private static final byte[] TEST_BOOL_BYTE_ARRAY = new byte[]{1};
  private static final byte[] TEST_BYTE_STRING_BYTE_ARRAY = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
  private static final byte[] TEST_ENUM_BYTE_ARRAY = new byte[]{0, 0, 0, 2};
  private static final byte[] TEST_LITTLE_ENDIAN_ENUM_BYTE_ARRAY = new byte[]{2, 0, 0, 0};

  AnnotationMessageConverter converter = new AnnotationMessageConverter();
  AnnotationMessageConverter converterLittleEndian = new AnnotationMessageConverter(com.blerpc.proto.ByteOrder.LITTLE_ENDIAN);

  @Test
  public void createWithWrongByteOrder() {
    assertError(() -> new AnnotationMessageConverter(com.blerpc.proto.ByteOrder.DEFAULT),
        "Converter support only BIG_ENDIAN and LITTLE_ENDIAN byte orders.");
  }

  @Test
  public void serializeRequest_integer() throws Exception {
    assertThat(converter.serializeRequest(null, TestIntegerMessage.newBuilder()
        .setIntValue(intFrom(TEST_INT_BYTE_ARRAY))
        .build()))
        .isEqualTo(TEST_INT_BYTE_ARRAY);
    assertThat(converterLittleEndian.serializeRequest(null, TestIntegerMessage.newBuilder()
        .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
        .build()))
        .isEqualTo(TEST_INT_BYTE_ARRAY);
  }

  @Test
  public void serializeRequest_long() throws Exception {
    assertThat(converter.serializeRequest(null, TestLongMessage.newBuilder()
        .setLongValue(longFrom(TEST_LONG_BYTE_ARRAY))
        .build()))
        .isEqualTo(TEST_LONG_BYTE_ARRAY);
    assertThat(converterLittleEndian.serializeRequest(null, TestLongMessage.newBuilder()
        .setLongValue(littleEndianLongFrom(TEST_LONG_BYTE_ARRAY))
        .build()))
        .isEqualTo(TEST_LONG_BYTE_ARRAY);
  }

  @Test
  public void serializeRequest_enum() throws Exception {
    assertThat(converter.serializeRequest(null, TestEnumMessage.newBuilder()
        .setEnumValue(TestEnum.forNumber(intFrom(TEST_ENUM_BYTE_ARRAY)))
        .build()))
        .isEqualTo(TEST_ENUM_BYTE_ARRAY);
    assertThat(converterLittleEndian.serializeRequest(null, TestEnumMessage.newBuilder()
        .setEnumValue(TestEnum.forNumber(littleEndianIntFrom(TEST_LITTLE_ENDIAN_ENUM_BYTE_ARRAY)))
        .build()))
        .isEqualTo(TEST_LITTLE_ENDIAN_ENUM_BYTE_ARRAY);
  }

  @Test
  public void serializeRequest_boolean() throws Exception {
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
  public void serializeRequest_byteString() throws Exception {
    assertThat(converter.serializeRequest(null, TestByteStringMessage.newBuilder()
        .setByteStringValue(ByteString.copyFrom(TEST_BYTE_STRING_BYTE_ARRAY))
        .build()))
        .isEqualTo(TEST_BYTE_STRING_BYTE_ARRAY);
    assertThat(converterLittleEndian.serializeRequest(null, TestByteStringMessage.newBuilder()
        .setByteStringValue(ByteString.copyFrom(TEST_BYTE_STRING_BYTE_ARRAY))
        .build()))
        .isEqualTo(TEST_BYTE_STRING_BYTE_ARRAY);
  }

  @Test
  public void serializeRequest_nonPrimitiveFieldMessage() throws Exception {
    assertThat(converter.serializeRequest(null, TestNonPrimitiveFieldMessage.newBuilder()
        .setIntValue(intFrom(TEST_INT_BYTE_ARRAY))
        .setEmbeddedMessage(TestLongMessage.newBuilder()
            .setLongValue(longFrom(TEST_LONG_BYTE_ARRAY)))
        .build()))
        .isEqualTo(concatArrays(TEST_INT_BYTE_ARRAY, TEST_LONG_BYTE_ARRAY));
    assertThat(converterLittleEndian.serializeRequest(null, TestNonPrimitiveFieldMessage.newBuilder()
        .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
        .setEmbeddedMessage(TestLongMessage.newBuilder()
            .setLongValue(littleEndianLongFrom(TEST_LONG_BYTE_ARRAY)))
        .build()))
        .isEqualTo(concatArrays(TEST_INT_BYTE_ARRAY, TEST_LONG_BYTE_ARRAY));
  }

  @Test
  public void serializeRequest_withGaps() throws Exception {
    assertThat(converter.serializeRequest(null, TestMessageWithGaps.newBuilder()
        .setIntValue(intFrom(TEST_INT_BYTE_ARRAY))
        .setLongValue(longFrom(TEST_LONG_BYTE_ARRAY))
        .build()))
        .isEqualTo(concatArrays(TEST_INT_BYTE_ARRAY, new byte[]{0, 0}, TEST_LONG_BYTE_ARRAY));
  }

  @Test
  public void serializeRequest_sevenBytesLong() throws Exception {
    converter.serializeRequest(null, TestSevenBytesLongMessage.newBuilder()
        .setLongValue(5000000000L)
        .build());
  }

  @Test
  public void serializeRequest_threeBytesInteger() throws Exception {
    converter.serializeRequest(null, TestThreeBytesIntegerMessage.newBuilder()
        .setIntValue(5000)
        .build());
  }

  @Test
  public void serializeRequest_threeBytesEnum() throws Exception {
    converter.serializeRequest(null, TestThreeBytesEnumMessage.newBuilder()
        .setEnumValue(TestEnum.VALUE_1)
        .build());
  }

  @Test
  public void serializeRequest_empty() throws Exception {
    assertThat(converter.serializeRequest(null, TestEmptyMessage.getDefaultInstance())).isEmpty();
  }

  @Test
  public void serializeRequest_zeroBytes() throws Exception {
    assertThat(converter.serializeRequest(null, TestZeroBytesMessage.getDefaultInstance())).isEmpty();
  }

  @Test
  public void serializeRequest_wrongByteStringSize() throws Exception {
    assertError(() -> converter.serializeRequest(null, TestByteStringMessage.newBuilder()
        .setByteStringValue(ByteString.copyFrom(new byte[]{1, 2, 3, 4}))
        .build()
    ), "Declared size 8 of ByteString byte_string_value is not equal to ByteString real size 4");
  }

  @Test
  public void serializeRequest_messageWithFieldAndWithoutByteSize() throws Exception {
    assertError(() -> converter.serializeRequest(null, TestNoBytesSizeMessage.getDefaultInstance()),
        "A non empty message TestNoBytesSizeMessage doesn't have com.blerpc.message_extension annotation.");
  }

  @Test
  public void serializeRequest_noByteRange() throws Exception {
    assertError(() -> converter.serializeRequest(null, TestNoBytesRangeMessage.getDefaultInstance()),
        "Proto field int_value doesn't have com.blerpc.field_extension annotation");
  }

  @Test
  public void serializeRequest_zeroSizeRange() throws Exception {
    assertError(() -> converter.serializeRequest(null, TestZeroSizeRangeMessage.getDefaultInstance()),
        "Field int_value has from_bytes = 0 which must be less than to_bytes = 0");
  }

  @Test
  public void serializeRequest_negativeSizeRange() throws Exception {
    assertError(() -> converter.serializeRequest(null, TestNegativeSizeRangeMessage.getDefaultInstance()),
        "Field int_value has from_bytes = 1 which must be less than to_bytes = 0");
  }

  @Test
  public void serializeRequest_negativeFrom() throws Exception {
    assertError(() -> converter.serializeRequest(null, TestNegativeRangeFromMessage.getDefaultInstance()),
        "Field int_value has from_bytes = -1 which is less than zero");
  }

  @Test
  public void serializeRequest_rangeBiggerThanCount() throws Exception {
    assertError(() -> converter.serializeRequest(null, TestRangeBiggerThanCountMessage.getDefaultInstance()),
        "Field int_value has to_bytes = 11 which is bigger than message bytes size = 10");
  }

  @Test
  public void serializeRequest_rangesIntersect() throws Exception {
    assertError(() -> converter.serializeRequest(null, TestRangesIntersectMessage.getDefaultInstance()),
        "Field int_value_1 bytes range [0, 4] intersects with another field int_value_2 bytes range [2, 10]");
  }

  @Test
  public void serializeRequest_unsupportedTypes() throws Exception {
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
  public void serializeRequest_wrongIntegerRange() throws Exception {
    assertError(() -> converter.serializeRequest(null, TestWrongIntegerRangeMessage.newBuilder()
            .setIntValue(20)
            .build()),
        "Int32 field int_value has unsupported size 5. Only sizes in [1, 4] are supported.");
  }

  @Test
  public void serializeRequest_wrongLongRange() throws Exception {
    assertError(() -> converter.serializeRequest(null, TestWrongLongRangeMessage.newBuilder()
            .setLongValue(100000)
            .build()),
        "Int64 field long_value has unsupported size 9. Only sizes in [1, 8] are supported.");
  }

  @Test
  public void serializeRequest_wrongEnumRange() throws Exception {
    assertError(() -> converter.serializeRequest(null, TestWrongEnumRangeMessage.newBuilder()
            .setEnumValue(TestEnum.VALUE_1)
            .build()),
        "Enum TestEnum field enum_value has unsupported size 5. Only sizes in [1, 4] are supported.");
  }

  @Test
  public void serializeRequest_wrongBooleanRange() throws Exception {
    assertError(() -> converter.serializeRequest(null, TestWrongBooleanRangeMessage.newBuilder()
            .setBoolValue(true)
            .build()),
        "Boolean field bool_value has unsupported size 2. Only sizes 1 are supported.");
  }

  @Test
  public void serializeRequest_notEnoughRangeForEnum() throws Exception {
    assertError(() -> converter.serializeRequest(null, TestSmallEnumRangeMessage.newBuilder()
            .setEnumValue(TestBigValueEnum.ENUM_VALUE_1)
            .build()),
        "3 byte(s) not enough for TestBigValueEnum enum that has 222222222 max number");
  }

  @Test
  public void serializeRequest_bigEndianMessage() throws Exception {
    assertThat(converter.serializeRequest(null, TestBigEndianMessage.newBuilder()
        .setIntValue(intFrom(TEST_INT_BYTE_ARRAY))
        .build()))
        .isEqualTo(TEST_INT_BYTE_ARRAY);
    assertThat(converterLittleEndian.serializeRequest(null, TestBigEndianMessage.newBuilder()
        .setIntValue(intFrom(TEST_INT_BYTE_ARRAY))
        .build()))
        .isEqualTo(TEST_INT_BYTE_ARRAY);
  }

  @Test
  public void serializeRequest_littleEndianMessage() throws Exception {
    assertThat(converter.serializeRequest(null, TestLittleEndianMessage.newBuilder()
        .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
        .build()))
        .isEqualTo(TEST_INT_BYTE_ARRAY);
    assertThat(converterLittleEndian.serializeRequest(null, TestLittleEndianMessage.newBuilder()
        .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
        .build()))
        .isEqualTo(TEST_INT_BYTE_ARRAY);
  }

  @Test
  public void serializeRequest_fieldOrderOverrideMessage() throws Exception {
    assertThat(converter.serializeRequest(null, TestFieldOrderOverrideMessage.newBuilder()
        .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
        .build()))
        .isEqualTo(TEST_INT_BYTE_ARRAY);
  }

  @Test
  public void serializeRequest_embeddedMessageNotContainsOrder() throws Exception {
    assertThat(converter.serializeRequest(null, TestEmbeddedMessageNotContainsOrder.newBuilder()
        .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
        .setEmbeddedMessage(TestIntegerMessage.newBuilder()
            .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY)))
        .build()))
        .isEqualTo(concatArrays(TEST_INT_BYTE_ARRAY, TEST_INT_BYTE_ARRAY));
  }

  @Test
  public void serializeRequest_overloadMessageOrderInEmbeddedMessage() throws Exception {
    assertThat(converter.serializeRequest(null, TestEmbeddedMessageOrderOverrideMessage.newBuilder()
        .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
        .setEmbeddedMessage(TestBigEndianMessage.newBuilder()
            .setIntValue(intFrom(TEST_INT_BYTE_ARRAY)))
        .build()))
        .isEqualTo(concatArrays(TEST_INT_BYTE_ARRAY, TEST_INT_BYTE_ARRAY));
  }

  @Test
  public void deserializeResponse_integer() throws Exception {
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
  }

  @Test
  public void deserializeResponse_long() throws Exception {
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
  }

  @Test
  public void deserializeResponse_enum() throws Exception {
    assertThat(converter.deserializeResponse(null, TestEnumMessage.getDefaultInstance(),
        TEST_ENUM_BYTE_ARRAY))
        .isEqualTo(TestEnumMessage.newBuilder()
            .setEnumValue(TestEnum.forNumber(intFrom(TEST_ENUM_BYTE_ARRAY)))
            .build());
    assertThat(converterLittleEndian.deserializeResponse(null, TestEnumMessage.getDefaultInstance(),
        TEST_LITTLE_ENDIAN_ENUM_BYTE_ARRAY))
        .isEqualTo(TestEnumMessage.newBuilder()
            .setEnumValue(TestEnum.forNumber(littleEndianIntFrom(TEST_LITTLE_ENDIAN_ENUM_BYTE_ARRAY)))
            .build());
  }

  @Test
  public void deserializeResponse_boolean() throws Exception {
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
  public void deserializeResponse_byteString() throws Exception {
    assertThat(converter.deserializeResponse(null, TestByteStringMessage.getDefaultInstance(),
        TEST_BYTE_STRING_BYTE_ARRAY))
        .isEqualTo(TestByteStringMessage.newBuilder()
            .setByteStringValue(ByteString.copyFrom(TEST_BYTE_STRING_BYTE_ARRAY))
            .build());
    assertThat(converterLittleEndian.deserializeResponse(null, TestByteStringMessage.getDefaultInstance(),
        TEST_BYTE_STRING_BYTE_ARRAY))
        .isEqualTo(TestByteStringMessage.newBuilder()
            .setByteStringValue(ByteString.copyFrom(TEST_BYTE_STRING_BYTE_ARRAY))
            .build());
  }

  @Test
  public void deserializeResponse_embeddedMessage() throws Exception {
    assertThat(converter.deserializeResponse(null, TestNonPrimitiveFieldMessage.getDefaultInstance(),
        concatArrays(TEST_INT_BYTE_ARRAY, TEST_LONG_BYTE_ARRAY)))
        .isEqualTo(TestNonPrimitiveFieldMessage.newBuilder()
            .setIntValue(intFrom(TEST_INT_BYTE_ARRAY))
            .setEmbeddedMessage(TestLongMessage.newBuilder()
                .setLongValue(longFrom(TEST_LONG_BYTE_ARRAY)))
            .build());
    assertThat(converterLittleEndian.deserializeResponse(null, TestNonPrimitiveFieldMessage.getDefaultInstance(),
        concatArrays(TEST_INT_BYTE_ARRAY, TEST_LONG_BYTE_ARRAY)))
        .isEqualTo(TestNonPrimitiveFieldMessage.newBuilder()
            .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
            .setEmbeddedMessage(TestLongMessage.newBuilder()
                .setLongValue(littleEndianLongFrom(TEST_LONG_BYTE_ARRAY)))
            .build());
  }

  @Test
  public void deserializeResponse_withGaps() throws Exception {
    assertThat(converter.deserializeResponse(null, TestMessageWithGaps.getDefaultInstance(),
        concatArrays(TEST_INT_BYTE_ARRAY, new byte[]{0, 0}, TEST_LONG_BYTE_ARRAY)))
        .isEqualTo(TestMessageWithGaps.newBuilder()
            .setIntValue(intFrom(TEST_INT_BYTE_ARRAY))
            .setLongValue(longFrom(TEST_LONG_BYTE_ARRAY))
            .build());
  }

  @Test
  public void deserializeResponse_emptyMessage() throws Exception {
    assertThat(converter.deserializeResponse(null, TestEmptyMessage.getDefaultInstance(), new byte[0]))
        .isEqualTo(TestEmptyMessage.getDefaultInstance());
  }

  @Test
  public void deserializeResponse_wrongMessageByteSize() throws Exception {
    assertError(() -> converter.deserializeResponse(null, TestIntegerMessage.getDefaultInstance(), new byte[10]),
        "Declared size 4 of message TestIntegerMessage is not equal to device response size 10");
  }

  @Test
  public void deserializeResponse_messageWithFieldAndWithoutByteSize() throws Exception {
    assertError(() -> converter.deserializeResponse(null,
        TestNoBytesSizeMessage.getDefaultInstance(),
        TEST_BOOL_BYTE_ARRAY),
        "A non empty message TestNoBytesSizeMessage doesn't have com.blerpc.message_extension annotation.");
  }

  @Test
  public void deserializeResponse_noByteRangeMessage() throws Exception {
    assertError(() -> converter.deserializeResponse(null, TestNoBytesRangeMessage.getDefaultInstance(), new byte[2]),
        "Proto field int_value doesn't have com.blerpc.field_extension annotation");
  }

  @Test
  public void deserializeResponse_zeroSizeRangeMessage() throws Exception {
    assertError(() -> converter.deserializeResponse(null, TestZeroSizeRangeMessage.getDefaultInstance(), new byte[1]),
        "Field int_value has from_bytes = 0 which must be less than to_bytes = 0");
  }

  @Test
  public void deserializeResponse_negativeRangeMessage() throws Exception {
    assertError(() -> converter.deserializeResponse(null, TestNegativeRangeFromMessage.getDefaultInstance(), new byte[1]),
        "Field int_value has from_bytes = -1 which is less than zero");
  }

  @Test
  public void deserializeResponse_rangeBiggerThanCountMessage() throws Exception {
    assertError(() -> converter.deserializeResponse(null, TestRangeBiggerThanCountMessage.getDefaultInstance(), new byte[10]),
        "Field int_value has to_bytes = 11 which is bigger than message bytes size = 10");
  }

  @Test
  public void deserializeResponse_unsupportedTypes() throws Exception {
    assertError(() -> converter.deserializeResponse(null, TestStringValueMessage.getDefaultInstance(), new byte[4]),
        "Unsupported field type: STRING");
    assertError(() -> converter.deserializeResponse(null, TestFloatValueMessage.getDefaultInstance(), new byte[4]),
        "Unsupported field type: FLOAT");
    assertError(() -> converter.deserializeResponse(null, TestDoubleValueMessage.getDefaultInstance(), new byte[8]),
        "Unsupported field type: DOUBLE");
  }

  @Test
  public void deserializeResponse_wrongIntegerRange() throws Exception {
    assertError(() -> converter.deserializeResponse(null, TestWrongIntegerRangeMessage.getDefaultInstance(), new byte[5]),
        "Int32 field int_value has unsupported size 5. Only sizes in [1, 4] are supported.");
  }

  @Test
  public void deserializeResponse_wrongLongRange() throws Exception {
    assertError(() -> converter.deserializeResponse(null, TestWrongLongRangeMessage.getDefaultInstance(), new byte[9]),
        "Int64 field long_value has unsupported size 9. Only sizes in [1, 8] are supported.");
  }

  @Test
  public void deserializeResponse_wrongEnumRange() throws Exception {
    assertError(() -> converter.deserializeResponse(null, TestWrongEnumRangeMessage.getDefaultInstance(), new byte[5]),
        "Enum TestEnum field enum_value has unsupported size 5. Only sizes in [1, 4] are supported.");
  }

  @Test
  public void deserializeResponse_wrongBooleanRange() throws Exception {
    assertError(() -> converter.deserializeResponse(null, TestWrongBooleanRangeMessage.getDefaultInstance(), new byte[2]),
        "Boolean field bool_value has unsupported size 2. Only sizes 1 are supported.");
  }

  @Test
  public void deserializeResponse_bigEndianMessage() throws Exception {
    assertThat(converter.deserializeResponse(null, TestBigEndianMessage.getDefaultInstance(),
        TEST_INT_BYTE_ARRAY))
        .isEqualTo(TestBigEndianMessage.newBuilder()
            .setIntValue(intFrom(TEST_INT_BYTE_ARRAY))
            .build());
    assertThat(converterLittleEndian.deserializeResponse(null, TestBigEndianMessage.getDefaultInstance(),
        TEST_INT_BYTE_ARRAY))
        .isEqualTo(TestBigEndianMessage.newBuilder()
            .setIntValue(intFrom(TEST_INT_BYTE_ARRAY))
            .build());
  }

  @Test
  public void deserializeResponse_littleEndianMessage() throws Exception {
    assertThat(converter.deserializeResponse(null, TestLittleEndianMessage.getDefaultInstance(),
        TEST_INT_BYTE_ARRAY))
        .isEqualTo(TestLittleEndianMessage.newBuilder()
            .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
            .build());
    assertThat(converterLittleEndian.deserializeResponse(null, TestLittleEndianMessage.getDefaultInstance(),
        TEST_INT_BYTE_ARRAY))
        .isEqualTo(TestLittleEndianMessage.newBuilder()
            .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
            .build());
  }

  @Test
  public void deserializeResponse_fieldOrderOverrideMessage() throws Exception {
    assertThat(converter.deserializeResponse(null, TestFieldOrderOverrideMessage.getDefaultInstance(),
        TEST_INT_BYTE_ARRAY))
        .isEqualTo(TestFieldOrderOverrideMessage.newBuilder()
            .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
            .build());
  }

  @Test
  public void deserializeResponse_embeddedMessageNotContainsOrder() throws Exception {
    assertThat(converter.deserializeResponse(null, TestEmbeddedMessageNotContainsOrder.getDefaultInstance(),
        concatArrays(TEST_INT_BYTE_ARRAY, TEST_INT_BYTE_ARRAY)))
        .isEqualTo(TestEmbeddedMessageNotContainsOrder.newBuilder()
            .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
            .setEmbeddedMessage(TestIntegerMessage.newBuilder()
                .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY)))
            .build());
  }

  @Test
  public void deserializeResponse_embeddedMessageOrderOverrideMessage() throws Exception {
    assertThat(converter.deserializeResponse(null, TestEmbeddedMessageOrderOverrideMessage.getDefaultInstance(),
        concatArrays(TEST_INT_BYTE_ARRAY, TEST_INT_BYTE_ARRAY)))
        .isEqualTo(TestEmbeddedMessageOrderOverrideMessage.newBuilder()
            .setIntValue(littleEndianIntFrom(TEST_INT_BYTE_ARRAY))
            .setEmbeddedMessage(TestBigEndianMessage.newBuilder()
                .setIntValue(intFrom(TEST_INT_BYTE_ARRAY)))
            .build());
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
    byte[] assembledArray = firstArray;
    for (byte[] array : arrays) {
      assembledArray = Bytes.concat(assembledArray, array);
    }
    return assembledArray;
  }
}
