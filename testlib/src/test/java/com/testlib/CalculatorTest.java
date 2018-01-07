package com.testlib;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link Calculator} class.
 */
public class CalculatorTest {
 
    private Calculator calculator;

    /**
     * Set up.
     */
    @Before
    public void setUp() {
        calculator = new Calculator();
    }
 
    @Test
    public void addition() {
        assertThat(calculator.add(1, 2)).isEqualTo(3);
    }
}
