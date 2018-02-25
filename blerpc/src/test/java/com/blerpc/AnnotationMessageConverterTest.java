package com.blerpc;

import static com.blerpc.Assert.assertError;
import static com.google.common.truth.Truth.assertThat;

import com.blerpc.device.test.proto.TestImageService;
import com.blerpc.device.test.proto.TestMetadata;
import com.blerpc.device.test.proto.TestOptionsDoubleValueRequest;
import com.blerpc.device.test.proto.TestOptionsEqualsIndexesRequest;
import com.blerpc.device.test.proto.TestOptionsFloatValueRequest;
import com.blerpc.device.test.proto.TestOptionsImage;
import com.blerpc.device.test.proto.TestOptionsImageRequest;
import com.blerpc.device.test.proto.TestOptionsNegativeRangeRequest;
import com.blerpc.device.test.proto.TestOptionsNoBytesRangeRequest;
import com.blerpc.device.test.proto.TestOptionsRangeBiggerThanCountRequest;
import com.blerpc.device.test.proto.TestOptionsRangeIntersectRequest;
import com.blerpc.device.test.proto.TestOptionsStringValueRequest;
import com.blerpc.device.test.proto.TestOptionsWithFieldNoBytesRequest;
import com.blerpc.device.test.proto.TestOptionsWrongBooleanRangeRequest;
import com.blerpc.device.test.proto.TestOptionsWrongEnumRangeRequest;
import com.blerpc.device.test.proto.TestOptionsWrongIntegerRangeRequest;
import com.blerpc.device.test.proto.TestOptionsWrongLongRangeRequest;
import com.blerpc.device.test.proto.TestOptionsZeroBytesRequest;
import com.blerpc.device.test.proto.TestType;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.MethodDescriptor;
import java.nio.ByteOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link AnnotationMessageConverter}.
 */
@RunWith(MockitoJUnitRunner.class)
public class AnnotationMessageConverterTest {

    private static final ByteString METADATA_STRING = ByteString.copyFrom(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
    private static final TestOptionsImage IMAGE_REQUEST_1 = TestOptionsImage.newBuilder()
            .setVersion(20)
            .setCrc(500)
            .setLength(100000)
            .setRelease(true)
            .setType(TestType.FULL)
            .setMetadata(TestMetadata.newBuilder()
                    .setMetadata(METADATA_STRING))
            .setBuildTime(1519548579757L)
            .build();
    private static final TestOptionsImage IMAGE_REQUEST_2 = TestOptionsImage.newBuilder()
            .setVersion(35)
            .setCrc(800)
            .setLength(130500)
            .setRelease(false)
            .setType(TestType.ONLY_APP)
            .setMetadata(TestMetadata.newBuilder()
                    .setMetadata(ByteString.copyFrom(new byte[]{5, 4, 3, 2, 1, 10, 9, 8, 7, 6})))
            .setBuildTime(1519576271989L)
            .build();
    private static final MethodDescriptor SEND_IMAGE_DESCRIPTOR = TestImageService.getDescriptor().findMethodByName("TestSendImage");
    private static final MethodDescriptor SEND_EMPTY_IMAGE_DESCRIPTOR = TestImageService.getDescriptor().findMethodByName("TestSendEmptyImage");
    private static final TestOptionsImage IMAGE_REQUEST_WRONG_BYTE_STRING = TestOptionsImage.newBuilder()
            .setMetadata(TestMetadata.newBuilder()
                    .setMetadata(ByteString.copyFrom(new byte[]{1, 2, 3})))
            .build();
    private static final byte[] EMPTY_ARRAY = new byte[0];
    private static final byte[] IMAGE_REQUEST_1_BYTES_LITTLE_ENDIAN =
            new byte[]{20, -12, 1, -96, -122, 1, 0, 1, 2, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, -83, 63, 39, -52, 97, 1, 0, 0};
    private static final byte[] IMAGE_REQUEST_1_BYTES_BIG_ENDIAN =
            new byte[]{20, 1, -12, 0, 1, -122, -96, 1, 2, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 0, 0, 1, 97, -52, 39, 63, -83};
    private static final byte[] IMAGE_REQUEST_2_BYTES_LITTLE_ENDIAN =
            new byte[]{35, 32, 3, -60, -3, 1, 0, 0, 1, 5, 4, 3, 2, 1, 10, 9, 8, 7, 6, 117, -52, -51, -51, 97, 1, 0, 0};
    private static final byte[] IMAGE_REQUEST_2_BYTES_BIG_ENDIAN =
            new byte[]{35, 3, 32, 0, 1, -3, -60, 0, 1, 5, 4, 3, 2, 1, 10, 9, 8, 7, 6, 0, 0, 1, 97, -51, -51, -52, 117};
    private static final TestOptionsZeroBytesRequest ZERO_BYTES_REQUEST = TestOptionsZeroBytesRequest.getDefaultInstance();
    private static final TestOptionsWithFieldNoBytesRequest FIELD_NO_BYTES_REQUEST = TestOptionsWithFieldNoBytesRequest.getDefaultInstance();
    private static final MethodDescriptor FIELD_NO_BYTES_DESCRIPTOR = TestImageService.getDescriptor()
            .findMethodByName("TestWithFieldNoBytesMethod");
    private static final TestOptionsNoBytesRangeRequest NO_BYTES_RANGE_REQUEST = TestOptionsNoBytesRangeRequest.newBuilder()
            .setValue(20)
            .build();
    private static final MethodDescriptor NO_BYTES_RANGE_DESCRIPTOR = TestImageService.getDescriptor()
            .findMethodByName("TestNoBytesRangeMethod");
    private static final TestOptionsEqualsIndexesRequest EQUALS_INDEXES_REQUEST = TestOptionsEqualsIndexesRequest.newBuilder()
            .setMetadata(METADATA_STRING)
            .build();
    private static final MethodDescriptor EQUALS_INDEXES_DESCRIPTOR = TestImageService.getDescriptor()
            .findMethodByName("TestEqualsIndexesMethod");
    private static final TestOptionsNegativeRangeRequest NEGATIVE_RANGE_REQUEST = TestOptionsNegativeRangeRequest.newBuilder()
            .setMetadata(METADATA_STRING)
            .build();
    private static final MethodDescriptor NEGATIVE_RANGE_DESCRIPTOR = TestImageService.getDescriptor()
            .findMethodByName("TestNegativeRangeMethod");
    private static final TestOptionsStringValueRequest STRING_VALUE_REQUEST = TestOptionsStringValueRequest.newBuilder()
            .setMessage("Message")
            .build();
    private static final MethodDescriptor STRING_VALUE_DESCRIPTOR = TestImageService.getDescriptor()
            .findMethodByName("TestStringValueMethod");
    private static final TestOptionsFloatValueRequest FLOAT_VALUE_REQUEST = TestOptionsFloatValueRequest.newBuilder()
            .setWeight(85.5f)
            .build();
    private static final MethodDescriptor FLOAT_VALUE_DESCRIPTOR = TestImageService.getDescriptor()
            .findMethodByName("TestFloatValueMethod");
    private static final TestOptionsDoubleValueRequest DOUBLE_VALUE_REQUEST = TestOptionsDoubleValueRequest.newBuilder()
            .setImpedance(300.5678d)
            .build();
    private static final MethodDescriptor DOUBLE_VALUE_DESCRIPTOR = TestImageService.getDescriptor()
            .findMethodByName("TestDoubleValueMethod");
    private static final TestOptionsRangeBiggerThanCountRequest RANGE_BIGGER_COUNT_REQUEST = TestOptionsRangeBiggerThanCountRequest.newBuilder()
            .setMetadata(METADATA_STRING)
            .build();
    private static final MethodDescriptor RANGE_BIGGER_COUNT_DESCRIPTOR = TestImageService.getDescriptor()
            .findMethodByName("TestRangeBiggerCountMethod");
    private static final TestOptionsWrongIntegerRangeRequest WRONG_INT_RANGE_REQUEST = TestOptionsWrongIntegerRangeRequest.newBuilder()
            .setValue(20)
            .build();
    private static final MethodDescriptor WRONG_INT_RANGE_DESCRIPTOR = TestImageService.getDescriptor()
            .findMethodByName("TestWrongIntegerRangeMethod");
    private static final TestOptionsWrongLongRangeRequest WRONG_LONG_RANGE_REQUEST = TestOptionsWrongLongRangeRequest.newBuilder()
            .setValue(100000)
            .build();
    private static final MethodDescriptor WRONG_LONG_RANGE_DESCRIPTOR = TestImageService.getDescriptor()
            .findMethodByName("TestWrongLongRangeMethod");
    private static final TestOptionsWrongEnumRangeRequest WRONG_ENUM_RANGE_REQUEST = TestOptionsWrongEnumRangeRequest.newBuilder()
            .setType(TestType.ONLY_APP)
            .build();
    private static final MethodDescriptor WRONG_ENUM_RANGE_DESCRIPTOR = TestImageService.getDescriptor()
            .findMethodByName("TestWrongEnumRangeMethod");
    private static final TestOptionsWrongBooleanRangeRequest WRONG_BOOLEAN_RANGE_REQUEST = TestOptionsWrongBooleanRangeRequest.newBuilder()
            .setRelease(true)
            .build();
    private static final MethodDescriptor WRONG_BOOLEAN_RANGE_DESCRIPTOR = TestImageService.getDescriptor()
            .findMethodByName("TestWrongBooleanRangeMethod");
    private static final TestOptionsRangeIntersectRequest RANGE_INTERSECT_REQUEST = TestOptionsRangeIntersectRequest.newBuilder()
            .setValue(ByteString.copyFrom(new byte[]{1, 2, 3}))
            .setMetadata(METADATA_STRING)
            .build();

    AnnotationMessageConverter converter = new AnnotationMessageConverter();
    AnnotationMessageConverter converterLittleEndian = new AnnotationMessageConverter(ByteOrder.LITTLE_ENDIAN);

    @Test
    public void serializeRequestTest() throws Exception {
        assertThat(converterLittleEndian.serializeRequest(null, IMAGE_REQUEST_1)).isEqualTo(IMAGE_REQUEST_1_BYTES_LITTLE_ENDIAN);
        assertThat(converterLittleEndian.serializeRequest(null, IMAGE_REQUEST_2)).isEqualTo(IMAGE_REQUEST_2_BYTES_LITTLE_ENDIAN);
        assertThat(converter.serializeRequest(null, IMAGE_REQUEST_1)).isEqualTo(IMAGE_REQUEST_1_BYTES_BIG_ENDIAN);
        assertThat(converter.serializeRequest(null, IMAGE_REQUEST_2)).isEqualTo(IMAGE_REQUEST_2_BYTES_BIG_ENDIAN);
    }

    @Test
    public void serializeRequestTest_emptyMessage() throws Exception {
        assertThat(converter.serializeRequest(null, TestOptionsImageRequest.getDefaultInstance())).isEqualTo(EMPTY_ARRAY);
    }

    @Test
    public void serializeRequestTest_zeroBytesMessage() throws Exception {
        assertThat(converter.serializeRequest(null, ZERO_BYTES_REQUEST)).isEqualTo(EMPTY_ARRAY);
    }

    @Test
    public void serializeRequestTest_messageWithFieldAndWithoutByteSize() throws Exception {
        assertError(() -> converter.serializeRequest(null, FIELD_NO_BYTES_REQUEST),
                "Proto message \"TestOptionsWithFieldNoBytesRequest\" with fields must have BytesSize option");
    }

    @Test
    public void serializeRequestTest_noByteRangeMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, NO_BYTES_RANGE_REQUEST),
                "Proto field \"value\" must have ByteRange option");
    }

    @Test
    public void serializeRequestTest_equalsIndexesMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, EQUALS_INDEXES_REQUEST),
                "ByteRange beginning index must be lower than ending index, field name: metadata");
    }

    @Test
    public void serializeRequestTest_negativeRangeMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, NEGATIVE_RANGE_REQUEST),
                "ByteRange must have only positive values, field name: metadata");
    }

    @Test
    public void serializeRequestTest_rangeBiggerThanCountMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, RANGE_BIGGER_COUNT_REQUEST),
                "ByteRange ending index must not be bigger than message byte count, field name: metadata");
    }

    @Test
    public void serializeRequestTest_rangeIntersectMessage() throws Exception {
        assertError(() -> converter.serializeRequest(null, RANGE_INTERSECT_REQUEST),
                "ByteRange must not intersect with other fields ByteRange, field name: metadata");
    }

    @Test
    public void serializeRequestTest_unsupportedType() throws Exception {
        assertError(() -> converter.serializeRequest(null, STRING_VALUE_REQUEST), "Unsupported field type: STRING");
        assertError(() -> converter.serializeRequest(null, FLOAT_VALUE_REQUEST), "Unsupported field type: FLOAT");
        assertError(() -> converter.serializeRequest(null, DOUBLE_VALUE_REQUEST), "Unsupported field type: DOUBLE");
    }

    @Test
    public void serializeRequestTest_wrongIntegerRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, WRONG_INT_RANGE_REQUEST), "Number field \"value\" has wrong bytes count");
    }

    @Test
    public void serializeRequestTest_wrongLongRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, WRONG_LONG_RANGE_REQUEST), "Number field \"value\" has wrong bytes count");
    }

    @Test
    public void serializeRequestTest_wrongEnumRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, WRONG_ENUM_RANGE_REQUEST), "Number field \"type\" has wrong bytes count");
    }

    @Test
    public void serializeRequestTest_wrongBooleanRange() throws Exception {
        assertError(() -> converter.serializeRequest(null, WRONG_BOOLEAN_RANGE_REQUEST),
                "Boolean value \"release\" mustn't take more than 1 byte");
    }

    @Test
    public void serializeRequestTest_byteStringWrongSize() throws Exception {
        assertError(() -> converter.serializeRequest(null, IMAGE_REQUEST_WRONG_BYTE_STRING),
                "ByteString \"metadata\" size is not equals to field bytes count");
    }

    @Test
    public void deserializeResponseTest() throws Exception {
        assertThat(converterLittleEndian.deserializeResponse(SEND_IMAGE_DESCRIPTOR, IMAGE_REQUEST_1_BYTES_LITTLE_ENDIAN)).isEqualTo(IMAGE_REQUEST_1);
        assertThat(converter.deserializeResponse(SEND_IMAGE_DESCRIPTOR, IMAGE_REQUEST_1_BYTES_BIG_ENDIAN)).isEqualTo(IMAGE_REQUEST_1);
        assertThat(converterLittleEndian.deserializeResponse(SEND_IMAGE_DESCRIPTOR, IMAGE_REQUEST_2_BYTES_LITTLE_ENDIAN)).isEqualTo(IMAGE_REQUEST_2);
        assertThat(converter.deserializeResponse(SEND_IMAGE_DESCRIPTOR, IMAGE_REQUEST_2_BYTES_BIG_ENDIAN)).isEqualTo(IMAGE_REQUEST_2);
    }

    @Test
    public void deserializeResponseTest_emptyMessage() throws Exception {
        assertThat(converter.deserializeResponse(SEND_EMPTY_IMAGE_DESCRIPTOR, EMPTY_ARRAY)).isEqualTo(TestOptionsImageRequest.getDefaultInstance());
    }

    @Test
    public void deserializeResponseTest_wrongMessageByteSize() throws Exception {
        assertError(() -> converter.deserializeResponse(SEND_IMAGE_DESCRIPTOR, new byte[10]),
                "Message byte size is not equals to size of device response");
    }

    @Test
    public void deserializeResponseTest_messageWithFieldAndWithoutByteSize() throws Exception {
        assertError(() -> converter.deserializeResponse(FIELD_NO_BYTES_DESCRIPTOR, IMAGE_REQUEST_1_BYTES_LITTLE_ENDIAN),
                "Proto message \"TestOptionsWithFieldNoBytesRequest\" with fields must have BytesSize option");
    }

    @Test
    public void deserializeResponseTest_noByteRangeMessage() throws Exception {
        assertError(() -> converter.deserializeResponse(NO_BYTES_RANGE_DESCRIPTOR, new byte[2]),
                "Proto field \"value\" must have ByteRange option");
    }

    @Test
    public void deserializeResponseTest_equalsIndexesMessage() throws Exception {
        assertError(() -> converter.deserializeResponse(EQUALS_INDEXES_DESCRIPTOR, new byte[10]),
                "ByteRange beginning index must be lower than ending index, field name: metadata");
    }

    @Test
    public void deserializeResponseTest_negativeRangeMessage() throws Exception {
        assertError(() -> converter.deserializeResponse(NEGATIVE_RANGE_DESCRIPTOR, new byte[10]),
                "ByteRange must have only positive values, field name: metadata");
    }

    @Test
    public void deserializeResponseTest_rangeBiggerThanCountMessage() throws Exception {
        assertError(() -> converter.deserializeResponse(RANGE_BIGGER_COUNT_DESCRIPTOR, new byte[10]),
                "ByteRange ending index must not be bigger than message byte count, field name: metadata");
    }

    @Test
    public void deserializeResponseTest_unsupportedType() throws Exception {
        assertError(() -> converter.deserializeResponse(STRING_VALUE_DESCRIPTOR, new byte[4]), "Unsupported field type: STRING");
        assertError(() -> converter.deserializeResponse(FLOAT_VALUE_DESCRIPTOR, new byte[4]), "Unsupported field type: FLOAT");
        assertError(() -> converter.deserializeResponse(DOUBLE_VALUE_DESCRIPTOR, new byte[4]), "Unsupported field type: DOUBLE");
    }

    @Test
    public void deserializeResponseTest_wrongIntegerRange() throws Exception {
        assertError(() -> converter.deserializeResponse(WRONG_INT_RANGE_DESCRIPTOR, new byte[5]), "Number field \"value\" has wrong bytes count");
    }

    @Test
    public void deserializeResponseTest_wrongLongRange() throws Exception {
        assertError(() -> converter.deserializeResponse(WRONG_LONG_RANGE_DESCRIPTOR, new byte[9]), "Number field \"value\" has wrong bytes count");
    }

    @Test
    public void deserializeResponseTest_wrongEnumRange() throws Exception {
        assertError(() -> converter.deserializeResponse(WRONG_ENUM_RANGE_DESCRIPTOR, new byte[3]), "Number field \"type\" has wrong bytes count");
    }

    @Test
    public void deserializeResponseTest_wrongBooleanRange() throws Exception {
        assertError(() -> converter.deserializeResponse(WRONG_BOOLEAN_RANGE_DESCRIPTOR, new byte[2]),
                "Boolean value \"release\" mustn't take more than 1 byte");
    }
}
