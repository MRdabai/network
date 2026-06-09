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
        val WIFI_WEAK = NetworkProfile("弱WiFi", 200, 100, 0.01f, 0, 0)
        val G4 = NetworkProfile("4G", 100, 50, 0.005f, 0, 0)
        val G4_WEAK = NetworkProfile("弱4G", 400, 200, 0.02f, 4096, 8192)
        val G3 = NetworkProfile("3G", 600, 300, 0.03f, 1024, 2048)
        val G2 = NetworkProfile("2G", 1200, 500, 0.03f, 512, 1024)
        val SUBWAY = NetworkProfile("地铁/电梯", 1500, 1000, 0.05f, 512, 1024)
        val HIGH_SPEED_RAIL = NetworkProfile("高铁", 800, 1000, 0.03f, 1024, 2048)
        val UNSTABLE = NetworkProfile("断断续续", 1000, 2000, 0.10f, 512, 1024)
        val DISCONNECT = NetworkProfile("断网", 0, 0, 1.0f, 0, 0)

        val PRESETS = listOf(NORMAL, WIFI_WEAK, G4, G4_WEAK, G3, G2, SUBWAY, HIGH_SPEED_RAIL, UNSTABLE, DISCONNECT)
    }

    val isThrottled: Boolean get() = this != NORMAL
    val displayBandwidth: String
        get() = when {
            downlinkKbps <= 0 -> "不限速"
            downlinkKbps < 1024 -> "${downlinkKbps}kbps"
            else -> "${downlinkKbps / 1024}Mbps"
        }
}
