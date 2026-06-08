package com.weaknet.simulator.vpn

import java.nio.ByteBuffer

object PacketParser {
    const val PROTOCOL_TCP = 6
    const val PROTOCOL_UDP = 17
    const val PROTOCOL_ICMP = 1

    fun getIpVersion(packet: ByteArray): Int = (packet[0].toInt() shr 4) and 0x0F

    fun getHeaderLength(packet: ByteArray): Int = (packet[0].toInt() and 0x0F) * 4

    fun getProtocol(packet: ByteArray): Int = packet[9].toInt() and 0xFF

    fun getTotalLength(packet: ByteArray): Int {
        return ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
    }

    fun getSourceIp(packet: ByteArray): ByteArray = packet.copyOfRange(12, 16)

    fun getDestIp(packet: ByteArray): ByteArray = packet.copyOfRange(16, 20)

    fun getSourcePort(packet: ByteArray): Int {
        val offset = getHeaderLength(packet)
        return ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
    }

    fun getDestPort(packet: ByteArray): Int {
        val offset = getHeaderLength(packet)
        return ((packet[offset + 2].toInt() and 0xFF) shl 8) or (packet[offset + 3].toInt() and 0xFF)
    }

    fun ipToString(ip: ByteArray): String {
        return ip.joinToString(".") { (it.toInt() and 0xFF).toString() }
    }

    fun isValidIpv4Packet(packet: ByteArray, length: Int): Boolean {
        if (length < 20) return false
        if (getIpVersion(packet) != 4) return false
        val headerLen = getHeaderLength(packet)
        if (headerLen < 20 || headerLen > length) return false
        return true
    }

    fun getUint32(packet: ByteArray, offset: Int): Long {
        return ((packet[offset].toLong() and 0xFF) shl 24) or
                ((packet[offset + 1].toLong() and 0xFF) shl 16) or
                ((packet[offset + 2].toLong() and 0xFF) shl 8) or
                (packet[offset + 3].toLong() and 0xFF)
    }
}
