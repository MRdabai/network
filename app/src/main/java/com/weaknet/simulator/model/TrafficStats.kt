package com.weaknet.simulator.model

data class TrafficStats(
    val uplinkBytes: Long = 0,
    val downlinkBytes: Long = 0,
    val uplinkPackets: Long = 0,
    val downlinkPackets: Long = 0,
    val droppedPackets: Long = 0,
    val delayedPackets: Long = 0
) {
    val totalPackets: Long get() = uplinkPackets + downlinkPackets
    val dropRate: Float
        get() = if (totalPackets > 0) droppedPackets.toFloat() / (totalPackets + droppedPackets) else 0f
}
