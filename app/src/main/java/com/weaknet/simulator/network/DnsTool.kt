package com.weaknet.simulator.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

data class DnsResult(
    val host: String,
    val addresses: List<String>,
    val elapsedMs: Long,
    val dnsServer: String?
)

object DnsTool {

    suspend fun lookup(host: String, dnsServer: String? = null): Result<DnsResult> = withContext(Dispatchers.IO) {
        try {
            val start = System.currentTimeMillis()
            val ips: List<String>

            if (dnsServer.isNullOrBlank()) {
                val addresses = InetAddress.getAllByName(host)
                ips = addresses.map { it.hostAddress ?: "unknown" }
            } else {
                ips = queryDnsServer(host, dnsServer)
            }

            val elapsed = System.currentTimeMillis() - start
            Result.success(DnsResult(host, ips, elapsed, dnsServer))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun queryDnsServer(host: String, server: String): List<String> {
        val query = buildDnsQuery(host)
        val serverAddr = InetAddress.getByName(server)
        val socket = DatagramSocket()
        socket.soTimeout = 5000

        try {
            val sendPacket = DatagramPacket(query, query.size, serverAddr, 53)
            socket.send(sendPacket)

            val buf = ByteArray(1024)
            val recvPacket = DatagramPacket(buf, buf.size)
            socket.receive(recvPacket)

            return parseDnsResponse(buf, recvPacket.length)
        } finally {
            socket.close()
        }
    }

    private fun buildDnsQuery(host: String): ByteArray {
        val buf = ByteBuffer.allocate(512)
        buf.putShort(0x1234) // Transaction ID
        buf.putShort(0x0100) // Flags: standard query, recursion desired
        buf.putShort(1)      // Questions: 1
        buf.putShort(0)      // Answers
        buf.putShort(0)      // Authority
        buf.putShort(0)      // Additional

        for (label in host.split(".")) {
            buf.put(label.length.toByte())
            buf.put(label.toByteArray())
        }
        buf.put(0) // End of domain name

        buf.putShort(1)  // Type A
        buf.putShort(1)  // Class IN

        val result = ByteArray(buf.position())
        buf.flip()
        buf.get(result)
        return result
    }

    private fun parseDnsResponse(data: ByteArray, length: Int): List<String> {
        val buf = ByteBuffer.wrap(data, 0, length)
        buf.getShort() // Transaction ID
        buf.getShort() // Flags
        val qdCount = buf.getShort().toInt() and 0xFFFF
        val anCount = buf.getShort().toInt() and 0xFFFF
        buf.getShort() // Authority
        buf.getShort() // Additional

        repeat(qdCount) { skipDnsName(buf); buf.getShort(); buf.getShort() }

        val results = mutableListOf<String>()
        repeat(anCount) {
            skipDnsName(buf)
            val type = buf.getShort().toInt() and 0xFFFF
            buf.getShort() // Class
            buf.getInt()   // TTL
            val rdLength = buf.getShort().toInt() and 0xFFFF

            if (type == 1 && rdLength == 4) {
                val ip = "${buf.get().toInt() and 0xFF}.${buf.get().toInt() and 0xFF}.${buf.get().toInt() and 0xFF}.${buf.get().toInt() and 0xFF}"
                results.add(ip)
            } else if (type == 28 && rdLength == 16) {
                val addr = ByteArray(16)
                buf.get(addr)
                results.add(InetAddress.getByAddress(addr).hostAddress ?: "unknown")
            } else {
                val skip = ByteArray(rdLength)
                buf.get(skip)
            }
        }

        return results.ifEmpty { listOf("No A/AAAA records found") }
    }

    private fun skipDnsName(buf: ByteBuffer) {
        while (buf.hasRemaining()) {
            val len = buf.get().toInt() and 0xFF
            if (len == 0) break
            if ((len and 0xC0) == 0xC0) { buf.get(); break }
            repeat(len) { buf.get() }
        }
    }
}
