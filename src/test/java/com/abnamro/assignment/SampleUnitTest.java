package com.abnamro.assignment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("UnitTest")
public class SampleUnitTest {

    //Note: This is just a sample - not part of the assignment

    @Test
    @DisplayName("TC1: Sample test case")
    void sampleTestMethod() {
        assertTrue(true);
    }
}
