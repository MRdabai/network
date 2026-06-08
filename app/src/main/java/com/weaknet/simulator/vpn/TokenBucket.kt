package com.weaknet.simulator.vpn

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class TokenBucket(
    private var rateKbps: Long,
    private var burstBytes: Long = rateKbps * 128
) {
    private var tokens: AtomicLong = AtomicLong(burstBytes)
    private var lastRefillNanos: Long = System.nanoTime()

    @Synchronized
    fun consume(bytes: Int): Long {
        if (rateKbps <= 0) return 0

        refill()
        val current = tokens.get()
        return if (current >= bytes) {
            tokens.addAndGet(-bytes.toLong())
            0L
        } else {
            val deficit = bytes - current
            tokens.set(0)
            (deficit * 8 * 1000) / (rateKbps * 1024)
        }
    }

    private fun refill() {
        val now = System.nanoTime()
        val elapsedMs = (now - lastRefillNanos) / 1_000_000
        if (elapsedMs <= 0) return

        val newTokens = (rateKbps * 1024 * elapsedMs) / (8 * 1000)
        val current = tokens.get()
        tokens.set(min(current + newTokens, burstBytes))
        lastRefillNanos = now
    }

    fun updateRate(newRateKbps: Long) {
        rateKbps = newRateKbps
        burstBytes = newRateKbps * 128
    }
}
