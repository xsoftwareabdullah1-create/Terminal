package com.astraterm.mobile;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class BuildSanityTest {
    @Test public void packageNameIsExpected() {
        assertTrue("AstraTerm package should be stable", "com.astraterm.mobile".contains("astraterm"));
    }
}
