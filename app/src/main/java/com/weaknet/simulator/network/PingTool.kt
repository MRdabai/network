package com.weaknet.simulator.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PingResult(
    val host: String,
    val packetsTransmitted: Int,
    val packetsReceived: Int,
    val lossPercent: Float,
    val minMs: Float,
    val avgMs: Float,
    val maxMs: Float,
    val rawOutput: String
)

object PingTool {

    suspend fun ping(host: String, count: Int = 4): Result<PingResult> = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("ping", "-c", count.toString(), "-W", "5", host))
            val output = process.inputStream.bufferedReader().readText()
            val errorOutput = process.errorStream.bufferedReader().readText()
            process.waitFor()

            if (output.isBlank()) {
                return@withContext Result.failure(Exception(errorOutput.ifBlank { "Ping failed: no response" }))
            }

            val statsRegex = Regex("""(\d+) packets transmitted, (\d+) (?:packets )?received.*?(\d+(?:\.\d+)?)% packet loss""")
            val statsMatch = statsRegex.find(output)

            val rttRegex = Regex("""rtt min/avg/max/mdev = ([\d.]+)/([\d.]+)/([\d.]+)/([\d.]+)""")
            val rttAlt = Regex("""min/avg/max/(?:mdev|stddev) = ([\d.]+)/([\d.]+)/([\d.]+)""")
            val rttMatch = rttRegex.find(output) ?: rttAlt.find(output)

            val transmitted = statsMatch?.groupValues?.get(1)?.toIntOrNull() ?: count
            val received = statsMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
            val loss = statsMatch?.groupValues?.get(3)?.toFloatOrNull() ?: 100f

            val min = rttMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            val avg = rttMatch?.groupValues?.get(2)?.toFloatOrNull() ?: 0f
            val max = rttMatch?.groupValues?.get(3)?.toFloatOrNull() ?: 0f

            Result.success(PingResult(host, transmitted, received, loss, min, avg, max, output))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
