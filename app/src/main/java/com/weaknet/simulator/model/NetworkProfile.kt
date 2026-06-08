package com.weaknet.simulator.model

data class NetworkProfile(
    val name: String,
    val latencyMs: Int,
    val jitterMs: Int,
    val lossRate: Float,
    val uplinkKbps: Long,
    val downlinkKbps: Long
) {
    companion object {
        val NORMAL = NetworkProfile("正常网络", 0, 0, 0f, 0, 0)
        val WIFI_WEAK = NetworkProfile("弱WiFi", 100, 50, 0.02f, 2048, 4096)
        val G4 = NetworkProfile("4G", 50, 30, 0.01f, 2048, 8192)
        val G4_WEAK = NetworkProfile("弱4G", 150, 80, 0.05f, 512, 2048)
        val G3 = NetworkProfile("3G", 200, 100, 0.05f, 128, 384)
        val G2 = NetworkProfile("2G", 500, 200, 0.1f, 16, 64)
        val SUBWAY = NetworkProfile("地铁/电梯", 800, 400, 0.15f, 20, 50)
        val HIGH_SPEED_RAIL = NetworkProfile("高铁", 150, 300, 0.08f, 150, 300)
        val UNSTABLE = NetworkProfile("断断续续", 300, 500, 0.30f, 64, 128)
        val DISCONNECT = NetworkProfile("断网", 0, 0, 1.0f, 0, 0)

        val PRESETS = listOf(NORMAL, WIFI_WEAK, G4, G4_WEAK, G3, G2, SUBWAY, HIGH_SPEED_RAIL, UNSTABLE, DISCONNECT)
    }

    val isThrottled: Boolean get() = this != NORMAL
    val displayBandwidth: String
        get() = when {
            downlinkKbps <= 0 -> "不限速"
            downlinkKbps < 1024 -> "${downlinkKbps}KB/s"
            else -> "${downlinkKbps / 1024}MB/s"
        }
}
