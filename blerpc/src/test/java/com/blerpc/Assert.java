package com.blerpc;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.fail;

/** A set of assert methods. Error will be thrown only when an assertion is wrong. */
public class Assert {

    private Assert() {}

    /**
     * Asserts that execution of the supplied runnable throws an exception with expected error message.
     * If no exception is thrown, or if an exception has different message, this method will fail.
     *
     * @param executable - executable that expected to throw an error.
     * @param expectedErrorMessage - expected command error message.
     */
    public static void assertError(Executable executable, String expectedErrorMessage) {
        try {
            executable.execute();
            fail(String.format("Error was expected: %s", expectedErrorMessage));
        } catch (Exception exception) {
            assertThat(exception.getMessage()).contains(expectedErrorMessage);
        }
    }

    public interface Executable {
        void execute() throws Exception;
    }
}
