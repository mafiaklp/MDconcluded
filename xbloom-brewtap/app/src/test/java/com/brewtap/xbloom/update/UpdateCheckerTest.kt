package com.brewtap.xbloom.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test fun comparesSemanticVersions() {
        assertTrue(UpdateChecker.isNewer("1.3.0", "1.2.0"))
        assertTrue(UpdateChecker.isNewer("v2.0.0", "1.99.99"))
        assertFalse(UpdateChecker.isNewer("1.2.0", "1.2.0"))
        assertFalse(UpdateChecker.isNewer("1.1.9", "1.2.0"))
    }
}
