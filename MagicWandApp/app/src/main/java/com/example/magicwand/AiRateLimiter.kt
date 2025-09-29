package com.example.magicwand

object AiRateLimiter {
    @Volatile private var lastCallAt = 0L
    const val MIN_GAP_MS = 10_000L   // 10s between AI calls app-wide

    fun gate(): Long {
        val now = System.currentTimeMillis()
        val wait = MIN_GAP_MS - (now - lastCallAt)
        return if (wait <= 0) { lastCallAt = now; 0L } else wait
    }
}
