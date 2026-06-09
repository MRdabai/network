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
        val WIFI_WEAK = NetworkProfile("弱WiFi", 300, 200, 0.02f, 0, 0)
        val G4 = NetworkProfile("4G", 150, 80, 0.01f, 0, 0)
        val G4_WEAK = NetworkProfile("弱4G", 500, 300, 0.03f, 2048, 4096)
        val G3 = NetworkProfile("3G", 1000, 500, 0.05f, 512, 1024)
        val G2 = NetworkProfile("2G", 2000, 800, 0.05f, 256, 512)
        val SUBWAY = NetworkProfile("地铁/电梯", 2500, 1500, 0.10f, 256, 512)
        val HIGH_SPEED_RAIL = NetworkProfile("高铁", 1000, 1500, 0.05f, 512, 1024)
        val UNSTABLE = NetworkProfile("断断续续", 1500, 3000, 0.15f, 256, 512)
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
