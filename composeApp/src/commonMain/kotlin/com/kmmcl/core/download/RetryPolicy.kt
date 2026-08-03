package com.kmmcl.core.download

/**
 * Retry policy for download operations.
 *
 * Mirrors PCL2 NetFile retry pattern: aggressive first retries,
 * then a longer wait for rate-limit backoff.
 */
data class RetryPolicy(
    val maxAttemptsPerSource: Int = 3,
    /** Delay in ms before retry N (0-indexed) */
    val backoffDelays: List<Long> = listOf(10_000L, 30_000L, 4_000L),
)
