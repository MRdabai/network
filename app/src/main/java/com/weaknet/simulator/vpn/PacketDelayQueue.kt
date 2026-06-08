package com.weaknet.simulator.vpn

import java.util.concurrent.DelayQueue
import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit

class DelayedPacket(
    val data: ByteArray,
    val length: Int,
    delayMs: Long
) : Delayed {
    private val triggerTimeNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs)

    override fun getDelay(unit: TimeUnit): Long {
        val remaining = triggerTimeNanos - System.nanoTime()
        return unit.convert(remaining, TimeUnit.NANOSECONDS)
    }

    override fun compareTo(other: Delayed): Int {
        return getDelay(TimeUnit.NANOSECONDS).compareTo(other.getDelay(TimeUnit.NANOSECONDS))
    }
}

class PacketDelayQueue {
    private val queue = DelayQueue<DelayedPacket>()

    fun enqueue(data: ByteArray, length: Int, delayMs: Long) {
        queue.put(DelayedPacket(data, length, delayMs))
    }

    fun take(): DelayedPacket = queue.take()

    fun poll(): DelayedPacket? = queue.poll()

    fun clear() = queue.clear()

    val size: Int get() = queue.size
}
