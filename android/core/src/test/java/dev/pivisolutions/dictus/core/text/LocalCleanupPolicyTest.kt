package dev.pivisolutions.dictus.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalCleanupPolicyTest {
    @Test fun preservesFacts() {
        assertNull(LocalCleanupPolicy.accept("do not send 15 dollars", "Send 50 dollars.[[END]]"))
        assertNull(LocalCleanupPolicy.accept("do not send the report", "Do send the report.[[END]]"))
    }
    @Test fun rejectsTruncation() {
        assertNull(LocalCleanupPolicy.accept("send the report tomorrow", "Send the report"))
    }
    @Test fun permitsPunctuationAndFillers() {
        assertEquals("Please send the report tomorrow.", LocalCleanupPolicy.accept("um please send the report tomorrow", "Please send the report tomorrow.[[END]]"))
    }
}
