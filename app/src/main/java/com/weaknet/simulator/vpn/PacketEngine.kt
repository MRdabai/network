package com.weaknet.simulator.vpn

import com.weaknet.simulator.model.NetworkProfile
import com.weaknet.simulator.model.TrafficStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

class PacketEngine(
    private val profileFlow: StateFlow<NetworkProfile>
) {
    private val uplinkBucket = TokenBucket(0)
    private val downlinkBucket = TokenBucket(0)
    val uplinkQueue = PacketDelayQueue()
    val downlinkQueue = PacketDelayQueue()

    private val _stats = MutableStateFlow(TrafficStats())
    val stats: StateFlow<TrafficStats> = _stats

    private var lastProfile: NetworkProfile? = null

    fun processUplinkPacket(data: ByteArray, length: Int): Boolean {
        val profile = profileFlow.value
        updateBucketsIfNeeded(profile)

        val halfLoss = profile.lossRate / 2f
        if (halfLoss > 0 && Random.nextFloat() < halfLoss) {
            updateStats { it.copy(droppedPackets = it.droppedPackets + 1) }
            return false
        }

        val networkDelay = computeDelay(profile)
        val shapingDelay = uplinkBucket.consume(length)
        val totalDelay = networkDelay + shapingDelay

        if (totalDelay > 0) {
            uplinkQueue.enqueue(data.copyOf(length), length, totalDelay)
            updateStats {
                it.copy(
                    uplinkPackets = it.uplinkPackets + 1,
                    uplinkBytes = it.uplinkBytes + length,
                    delayedPackets = it.delayedPackets + 1
                )
            }
        } else {
            updateStats {
                it.copy(
                    uplinkPackets = it.uplinkPackets + 1,
                    uplinkBytes = it.uplinkBytes + length
                )
            }
        }
        return totalDelay <= 0
    }

    fun processDownlinkPacket(data: ByteArray, length: Int): Boolean {
        val profile = profileFlow.value
        updateBucketsIfNeeded(profile)

        val halfLoss = profile.lossRate / 2f
        if (halfLoss > 0 && Random.nextFloat() < halfLoss) {
            updateStats { it.copy(droppedPackets = it.droppedPackets + 1) }
            return false
        }

        val networkDelay = computeDelay(profile)
        val shapingDelay = downlinkBucket.consume(length)
        val totalDelay = networkDelay + shapingDelay

        if (totalDelay > 0) {
            downlinkQueue.enqueue(data.copyOf(length), length, totalDelay)
            updateStats {
                it.copy(
                    downlinkPackets = it.downlinkPackets + 1,
                    downlinkBytes = it.downlinkBytes + length,
                    delayedPackets = it.delayedPackets + 1
                )
            }
        } else {
            updateStats {
                it.copy(
                    downlinkPackets = it.downlinkPackets + 1,
                    downlinkBytes = it.downlinkBytes + length
                )
            }
        }
        return totalDelay <= 0
    }

    fun resetStats() {
        _stats.value = TrafficStats()
    }

    private fun computeDelay(profile: NetworkProfile): Long {
        if (profile.latencyMs <= 0 && profile.jitterMs <= 0) return 0
        val base = profile.latencyMs.toLong() / 2
        val jitter = if (profile.jitterMs > 0) Random.nextInt(profile.jitterMs).toLong() / 2 else 0
        return base + jitter
    }

    private fun updateBucketsIfNeeded(profile: NetworkProfile) {
        if (profile != lastProfile) {
            uplinkBucket.updateRate(profile.uplinkKbps)
            downlinkBucket.updateRate(profile.downlinkKbps)
            lastProfile = profile
        }
    }

    private inline fun updateStats(transform: (TrafficStats) -> TrafficStats) {
        _stats.value = transform(_stats.value)
    }
}
