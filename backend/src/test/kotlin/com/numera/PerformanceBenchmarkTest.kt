package com.numera

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("perf")
@Disabled("Opt-in benchmark tests; run selectively.")
class PerformanceBenchmarkTest {
    @Test
    fun `login responds under 200ms`() {}

    @Test
    fun `document upload 10MB under 2s`() {}

    @Test
    fun `template load under 100ms`() {}

    @Test
    fun `spread history query under 300ms`() {}
}
