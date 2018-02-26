package com.blerpc;

import static com.blerpc.Assert.assertError;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import junit.framework.AssertionFailedError;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link Assert}.
 */
@RunWith(MockitoJUnitRunner.class)
public class AssertTest {

    private static final String ERROR_MESSAGE = "error message";
    private static final String WRONG_ERROR_MESSAGE = "wrong message";

    @Test
    public void assertError_throwError() {
        assertError(() -> {
            throw new RuntimeException(ERROR_MESSAGE);
        }, ERROR_MESSAGE);
    }

    @Test
    public void assertError_notThrowError() {
        try {
            assertError(() -> {
                // Do nothing
            }, ERROR_MESSAGE);
            fail("assertError() method must throw an error if execution had no errors");
        } catch (AssertionFailedError error) {
            assertThat(error.getMessage()).contains("Error was expected: error message");
        }
    }

    @Test
    public void assertError_wrongErrorMessage() {
        try {
            assertError(() -> {
                throw new RuntimeException(WRONG_ERROR_MESSAGE);
            }, ERROR_MESSAGE);
            fail("assertError() method must throw an error if execution error not contains expected message");
        } catch (AssertionError error) {
            assertThat(error.getMessage()).contains("Not true that <\"wrong message\"> contains <\"error message\">");
        }
    }
}
