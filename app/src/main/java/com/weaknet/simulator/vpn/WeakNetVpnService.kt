package com.weaknet.simulator.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.weaknet.simulator.model.NetworkProfile
import com.weaknet.simulator.model.TrafficStats
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class WeakNetVpnService : VpnService() {

    companion object {
        private const val TAG = "WeakNetVpn"
        private const val CHANNEL_ID = "weaknet_vpn"
        private const val NOTIFICATION_ID = 1
        private const val MTU = 1500
        private const val TUN_ADDRESS = "10.10.10.2"
        private const val SESSION_TIMEOUT_MS = 120_000L
        private const val CLEANUP_INTERVAL_MS = 30_000L

        val profileFlow = MutableStateFlow(NetworkProfile.NORMAL)
        val runningFlow = MutableStateFlow(false)
        val statsFlow: StateFlow<TrafficStats> get() = engine?.stats ?: MutableStateFlow(TrafficStats())

        private var engine: PacketEngine? = null
        private var targetApps = MutableStateFlow<Set<String>>(emptySet())

        fun updateProfile(profile: NetworkProfile) {
            profileFlow.value = profile
        }

        fun updateTargetApps(apps: Set<String>) {
            targetApps.value = apps
        }
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private var tunInput: FileInputStream? = null
    private var tunOutput: FileOutputStream? = null
    private var scope: CoroutineScope? = null
    private var selector: Selector? = null

    private val udpSessions = ConcurrentHashMap<String, UdpSession>()
    private val tcpSessions = ConcurrentHashMap<String, TcpSession>()
    private val pendingWrites = ConcurrentLinkedQueue<PendingWrite>()
    private val idGenerator = AtomicInteger(0)

    private data class UdpSession(
        val channel: DatagramChannel,
        val key: String,
        val srcIp: ByteArray,
        val srcPort: Int,
        val ipHeaderLen: Int,
        val originalPacketHeader: ByteArray,
        var lastActive: Long = System.currentTimeMillis()
    )

    private data class TcpSession(
        val channel: SocketChannel,
        val key: String,
        val srcIp: ByteArray,
        val srcPort: Int,
        val dstIp: ByteArray,
        val dstPort: Int,
        var seqNum: Long,
        var ackNum: Long,
        var lastActive: Long = System.currentTimeMillis(),
        var state: TcpState = TcpState.SYN_RECEIVED
    )

    private enum class TcpState { SYN_RECEIVED, ESTABLISHED, FIN_WAIT, CLOSED }

    private data class PendingWrite(val data: ByteArray, val length: Int)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun startVpn() {
        if (tunInterface != null) return

        engine = PacketEngine(profileFlow)
        selector = Selector.open()

        val builder = Builder()
            .setSession("WeakNet")
            .addAddress(TUN_ADDRESS, 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("114.114.114.114")
            .setMtu(MTU)
            .setBlocking(true)

        val apps = targetApps.value
        if (apps.isNotEmpty()) {
            apps.forEach { pkg ->
                try { builder.addAllowedApplication(pkg) } catch (_: Exception) {}
            }
        }
        try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}

        tunInterface = builder.establish()
        if (tunInterface == null) {
            Log.e(TAG, "VPN establish failed")
            return
        }

        tunInput = FileInputStream(tunInterface!!.fileDescriptor)
        tunOutput = FileOutputStream(tunInterface!!.fileDescriptor)
        runningFlow.value = true

        startForeground(NOTIFICATION_ID, buildNotification())

        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope?.launch { tunReadLoop() }
        scope?.launch { uplinkDelayConsumer() }
        scope?.launch { downlinkDelayConsumer() }
        scope?.launch { selectorLoop() }
        scope?.launch { sessionCleanupLoop() }

        Log.i(TAG, "VPN started, profile=${profileFlow.value.name}")
    }

    private fun stopVpn() {
        runningFlow.value = false
        scope?.cancel()
        scope = null

        udpSessions.values.forEach { s -> try { s.channel.close() } catch (_: Exception) {} }
        udpSessions.clear()
        tcpSessions.values.forEach { s -> try { s.channel.close() } catch (_: Exception) {} }
        tcpSessions.clear()

        try { selector?.close() } catch (_: Exception) {}
        selector = null
        try { tunInput?.close() } catch (_: Exception) {}
        try { tunOutput?.close() } catch (_: Exception) {}
        try { tunInterface?.close() } catch (_: Exception) {}
        tunInput = null
        tunOutput = null
        tunInterface = null
        engine = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "VPN stopped")
    }

    // ===================== TUN READ =====================

    private suspend fun tunReadLoop() {
        val buffer = ByteArray(MTU)
        val input = tunInput ?: return

        while (scope?.isActive == true) {
            try {
                val length = input.read(buffer)
                if (length <= 0) continue
                if (!PacketParser.isValidIpv4Packet(buffer, length)) continue

                val eng = engine ?: continue
                val packetCopy = buffer.copyOf(length)

                val immediate = eng.processUplinkPacket(packetCopy, length)
                if (immediate) {
                    dispatchToNetwork(packetCopy, length)
                }
            } catch (e: Exception) {
                if (scope?.isActive == true) {
                    Log.w(TAG, "TUN read error: ${e.message}")
                    delay(100)
                }
            }
        }
    }

    // ===================== DELAY CONSUMERS =====================

    private suspend fun uplinkDelayConsumer() {
        while (scope?.isActive == true) {
            try {
                val pkt = engine?.uplinkQueue?.take() ?: break
                dispatchToNetwork(pkt.data, pkt.length)
            } catch (_: InterruptedException) { break }
            catch (e: Exception) {
                if (scope?.isActive == true) delay(50)
            }
        }
    }

    private suspend fun downlinkDelayConsumer() {
        val output = tunOutput ?: return
        while (scope?.isActive == true) {
            try {
                val pkt = engine?.downlinkQueue?.take() ?: break
                synchronized(output) { output.write(pkt.data, 0, pkt.length) }
            } catch (_: InterruptedException) { break }
            catch (e: Exception) {
                if (scope?.isActive == true) delay(50)
            }
        }
    }

    // ===================== DISPATCH =====================

    private fun dispatchToNetwork(packet: ByteArray, length: Int) {
        val protocol = PacketParser.getProtocol(packet)
        try {
            when (protocol) {
                PacketParser.PROTOCOL_UDP -> handleUdp(packet, length)
                PacketParser.PROTOCOL_TCP -> handleTcp(packet, length)
            }
        } catch (e: Exception) {
            Log.w(TAG, "dispatch error: ${e.message}")
        }
    }

    // ===================== UDP =====================

    private fun handleUdp(packet: ByteArray, length: Int) {
        val ipHeaderLen = PacketParser.getHeaderLength(packet)
        val srcIp = PacketParser.getSourceIp(packet)
        val srcPort = PacketParser.getSourcePort(packet)
        val dstIp = PacketParser.getDestIp(packet)
        val dstPort = PacketParser.getDestPort(packet)

        val key = "udp:${PacketParser.ipToString(srcIp)}:$srcPort"
        val payloadOffset = ipHeaderLen + 8
        val payloadLength = length - payloadOffset
        if (payloadLength <= 0) return

        val session = udpSessions.getOrPut(key) {
            val ch = DatagramChannel.open()
            ch.configureBlocking(false)
            protect(ch.socket())
            val sel = selector ?: return
            ch.register(sel, SelectionKey.OP_READ, key)
            sel.wakeup()
            UdpSession(
                channel = ch, key = key,
                srcIp = srcIp, srcPort = srcPort,
                ipHeaderLen = ipHeaderLen,
                originalPacketHeader = packet.copyOf(ipHeaderLen + 8)
            )
        }

        session.lastActive = System.currentTimeMillis()
        try {
            val dest = InetSocketAddress(InetAddress.getByAddress(dstIp), dstPort)
            val sendBuf = ByteBuffer.wrap(packet, payloadOffset, payloadLength)
            session.channel.send(sendBuf, dest)
        } catch (e: Exception) {
            Log.w(TAG, "UDP send error: ${e.message}")
            udpSessions.remove(key)
            try { session.channel.close() } catch (_: Exception) {}
        }
    }

    private fun handleUdpResponse(key: String) {
        val session = udpSessions[key] ?: return
        val buf = ByteBuffer.allocate(MTU)
        try {
            val from = session.channel.receive(buf) ?: return
            buf.flip()
            val payloadLen = buf.remaining()
            if (payloadLen <= 0) return

            val response = buildUdpResponse(session, buf, payloadLen)
            session.lastActive = System.currentTimeMillis()

            val eng = engine ?: return
            val immediate = eng.processDownlinkPacket(response, response.size)
            if (immediate) {
                val output = tunOutput ?: return
                synchronized(output) { output.write(response) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "UDP recv error: ${e.message}")
        }
    }

    private fun buildUdpResponse(session: UdpSession, payload: ByteBuffer, payloadLen: Int): ByteArray {
        val origHeader = session.originalPacketHeader
        val ipHeaderLen = session.ipHeaderLen
        val udpLen = 8 + payloadLen
        val totalLen = 20 + udpLen
        val response = ByteArray(totalLen)

        response[0] = 0x45.toByte()
        response[1] = 0
        response[2] = ((totalLen shr 8) and 0xFF).toByte()
        response[3] = (totalLen and 0xFF).toByte()
        response[4] = ((idGenerator.incrementAndGet() shr 8) and 0xFF).toByte()
        response[5] = (idGenerator.get() and 0xFF).toByte()
        response[6] = 0x40.toByte(); response[7] = 0
        response[8] = 64; response[9] = PacketParser.PROTOCOL_UDP.toByte()
        // swap IP
        System.arraycopy(origHeader, 16, response, 12, 4)
        System.arraycopy(origHeader, 12, response, 16, 4)
        // swap ports
        response[20] = origHeader[ipHeaderLen + 2]; response[21] = origHeader[ipHeaderLen + 3]
        response[22] = origHeader[ipHeaderLen]; response[23] = origHeader[ipHeaderLen + 1]
        // UDP length
        response[24] = ((udpLen shr 8) and 0xFF).toByte()
        response[25] = (udpLen and 0xFF).toByte()
        response[26] = 0; response[27] = 0
        payload.get(response, 28, payloadLen)
        computeIpChecksum(response)
        return response
    }

    // ===================== TCP =====================

    private fun handleTcp(packet: ByteArray, length: Int) {
        val ipHeaderLen = PacketParser.getHeaderLength(packet)
        val srcIp = PacketParser.getSourceIp(packet)
        val srcPort = PacketParser.getSourcePort(packet)
        val dstIp = PacketParser.getDestIp(packet)
        val dstPort = PacketParser.getDestPort(packet)

        val key = "tcp:${PacketParser.ipToString(srcIp)}:$srcPort:${PacketParser.ipToString(dstIp)}:$dstPort"
        val flags = packet[ipHeaderLen + 13].toInt() and 0xFF
        val isSyn = (flags and 0x02) != 0
        val isAck = (flags and 0x10) != 0
        val isFin = (flags and 0x01) != 0
        val isRst = (flags and 0x04) != 0

        val tcpHeaderLen = ((packet[ipHeaderLen + 12].toInt() shr 4) and 0x0F) * 4
        val seqNum = PacketParser.getUint32(packet, ipHeaderLen + 4)
        val ackNum = PacketParser.getUint32(packet, ipHeaderLen + 8)

        if (isRst) {
            closeTcpSession(key)
            return
        }

        if (isSyn && !isAck) {
            initTcpSession(key, srcIp, srcPort, dstIp, dstPort, seqNum, packet, ipHeaderLen)
            return
        }

        val session = tcpSessions[key] ?: return
        session.lastActive = System.currentTimeMillis()

        if (isFin) {
            sendTcpAck(session, packet, ipHeaderLen, seqNum + 1)
            closeTcpSession(key)
            return
        }

        val payloadOffset = ipHeaderLen + tcpHeaderLen
        val payloadLength = length - payloadOffset
        if (payloadLength > 0 && session.state == TcpState.ESTABLISHED) {
            try {
                val buf = ByteBuffer.wrap(packet, payloadOffset, payloadLength)
                session.channel.write(buf)
                session.ackNum = seqNum + payloadLength
            } catch (e: Exception) {
                Log.w(TAG, "TCP write error: ${e.message}")
                sendTcpRst(session, packet, ipHeaderLen)
                closeTcpSession(key)
            }
        }
    }

    private fun initTcpSession(
        key: String, srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int, clientSeq: Long,
        originalPacket: ByteArray, ipHeaderLen: Int
    ) {
        scope?.launch(Dispatchers.IO) {
            try {
                val ch = SocketChannel.open()
                ch.configureBlocking(false)
                protect(ch.socket())
                ch.connect(InetSocketAddress(InetAddress.getByAddress(dstIp), dstPort))

                val localSeq = System.nanoTime() and 0xFFFFFFFFL
                val session = TcpSession(
                    channel = ch, key = key,
                    srcIp = srcIp, srcPort = srcPort,
                    dstIp = dstIp, dstPort = dstPort,
                    seqNum = localSeq, ackNum = clientSeq + 1,
                    state = TcpState.SYN_RECEIVED
                )
                tcpSessions[key] = session

                val sel = selector ?: return@launch
                ch.register(sel, SelectionKey.OP_CONNECT, key)
                sel.wakeup()
            } catch (e: Exception) {
                Log.w(TAG, "TCP connect init error: ${e.message}")
                tcpSessions.remove(key)
            }
        }
    }

    private fun onTcpConnected(key: String) {
        val session = tcpSessions[key] ?: return
        try {
            if (session.channel.isConnectionPending) {
                session.channel.finishConnect()
            }
            session.state = TcpState.ESTABLISHED
            val sel = selector ?: return
            session.channel.register(sel, SelectionKey.OP_READ, key)

            val synAck = buildTcpPacket(
                session, TcpFlags.SYN or TcpFlags.ACK,
                session.seqNum, session.ackNum, null
            )
            session.seqNum++
            writeToTun(synAck)
        } catch (e: Exception) {
            Log.w(TAG, "TCP connect finish error: ${e.message}")
            closeTcpSession(key)
        }
    }

    private fun onTcpDataReady(key: String) {
        val session = tcpSessions[key] ?: return
        val buf = ByteBuffer.allocate(MTU - 40)
        try {
            val read = session.channel.read(buf)
            if (read <= 0) {
                sendTcpFin(session)
                closeTcpSession(key)
                return
            }
            buf.flip()
            val payload = ByteArray(read)
            buf.get(payload)

            val pkt = buildTcpPacket(
                session, TcpFlags.ACK or TcpFlags.PSH,
                session.seqNum, session.ackNum, payload
            )
            session.seqNum += read
            session.lastActive = System.currentTimeMillis()

            val eng = engine ?: return
            val immediate = eng.processDownlinkPacket(pkt, pkt.size)
            if (immediate) {
                writeToTun(pkt)
            }
        } catch (e: Exception) {
            Log.w(TAG, "TCP read error: ${e.message}")
            closeTcpSession(key)
        }
    }

    private fun sendTcpAck(session: TcpSession, origPacket: ByteArray, ipHeaderLen: Int, newAck: Long) {
        val pkt = buildTcpPacket(session, TcpFlags.ACK, session.seqNum, newAck, null)
        writeToTun(pkt)
    }

    private fun sendTcpRst(session: TcpSession, origPacket: ByteArray, ipHeaderLen: Int) {
        val pkt = buildTcpPacket(session, TcpFlags.RST or TcpFlags.ACK, session.seqNum, session.ackNum, null)
        writeToTun(pkt)
    }

    private fun sendTcpFin(session: TcpSession) {
        val pkt = buildTcpPacket(session, TcpFlags.FIN or TcpFlags.ACK, session.seqNum, session.ackNum, null)
        session.seqNum++
        writeToTun(pkt)
    }

    private fun closeTcpSession(key: String) {
        tcpSessions.remove(key)?.let { s ->
            try {
                s.channel.keyFor(selector)?.cancel()
                s.channel.close()
            } catch (_: Exception) {}
        }
    }

    private fun buildTcpPacket(
        session: TcpSession, flags: Int,
        seqNum: Long, ackNum: Long,
        payload: ByteArray?
    ): ByteArray {
        val tcpHeaderLen = 20
        val payloadLen = payload?.size ?: 0
        val totalLen = 20 + tcpHeaderLen + payloadLen
        val pkt = ByteArray(totalLen)

        // IP header (server -> client)
        pkt[0] = 0x45.toByte()
        pkt[2] = ((totalLen shr 8) and 0xFF).toByte()
        pkt[3] = (totalLen and 0xFF).toByte()
        val id = idGenerator.incrementAndGet()
        pkt[4] = ((id shr 8) and 0xFF).toByte()
        pkt[5] = (id and 0xFF).toByte()
        pkt[6] = 0x40.toByte()
        pkt[8] = 64
        pkt[9] = PacketParser.PROTOCOL_TCP.toByte()
        System.arraycopy(session.dstIp, 0, pkt, 12, 4) // src = server
        System.arraycopy(session.srcIp, 0, pkt, 16, 4) // dst = client

        // TCP header
        val t = 20 // TCP offset in packet
        pkt[t] = ((session.dstPort shr 8) and 0xFF).toByte()
        pkt[t + 1] = (session.dstPort and 0xFF).toByte()
        pkt[t + 2] = ((session.srcPort shr 8) and 0xFF).toByte()
        pkt[t + 3] = (session.srcPort and 0xFF).toByte()
        // seq
        pkt[t + 4] = ((seqNum shr 24) and 0xFF).toByte()
        pkt[t + 5] = ((seqNum shr 16) and 0xFF).toByte()
        pkt[t + 6] = ((seqNum shr 8) and 0xFF).toByte()
        pkt[t + 7] = (seqNum and 0xFF).toByte()
        // ack
        pkt[t + 8] = ((ackNum shr 24) and 0xFF).toByte()
        pkt[t + 9] = ((ackNum shr 16) and 0xFF).toByte()
        pkt[t + 10] = ((ackNum shr 8) and 0xFF).toByte()
        pkt[t + 11] = (ackNum and 0xFF).toByte()
        // data offset + flags
        pkt[t + 12] = (5 shl 4).toByte()
        pkt[t + 13] = flags.toByte()
        // window
        pkt[t + 14] = 0xFF.toByte(); pkt[t + 15] = 0xFF.toByte()

        if (payload != null) {
            System.arraycopy(payload, 0, pkt, t + tcpHeaderLen, payloadLen)
        }

        // TCP checksum
        computeTcpChecksum(pkt, 20, tcpHeaderLen + payloadLen)
        computeIpChecksum(pkt)
        return pkt
    }

    // ===================== SELECTOR LOOP =====================

    private suspend fun selectorLoop() {
        val sel = selector ?: return
        while (scope?.isActive == true) {
            try {
                val ready = sel.select(500)
                if (ready == 0) continue
                val keys = sel.selectedKeys().iterator()
                while (keys.hasNext()) {
                    val selKey = keys.next()
                    keys.remove()
                    if (!selKey.isValid) continue

                    val attachKey = selKey.attachment() as? String ?: continue
                    when {
                        selKey.isConnectable && attachKey.startsWith("tcp:") -> onTcpConnected(attachKey)
                        selKey.isReadable && attachKey.startsWith("tcp:") -> onTcpDataReady(attachKey)
                        selKey.isReadable && attachKey.startsWith("udp:") -> handleUdpResponse(attachKey)
                    }
                }
            } catch (e: Exception) {
                if (scope?.isActive == true) {
                    Log.w(TAG, "selector error: ${e.message}")
                    delay(100)
                }
            }
        }
    }

    // ===================== SESSION CLEANUP =====================

    private suspend fun sessionCleanupLoop() {
        while (scope?.isActive == true) {
            delay(CLEANUP_INTERVAL_MS)
            val now = System.currentTimeMillis()
            udpSessions.entries.removeIf { (_, s) ->
                if (now - s.lastActive > SESSION_TIMEOUT_MS) {
                    try { s.channel.close() } catch (_: Exception) {}
                    true
                } else false
            }
            tcpSessions.entries.removeIf { (_, s) ->
                if (now - s.lastActive > SESSION_TIMEOUT_MS) {
                    try { s.channel.close() } catch (_: Exception) {}
                    true
                } else false
            }
        }
    }

    // ===================== UTILS =====================

    private fun writeToTun(data: ByteArray) {
        val output = tunOutput ?: return
        try {
            synchronized(output) { output.write(data) }
        } catch (e: Exception) {
            Log.w(TAG, "TUN write error: ${e.message}")
        }
    }

    private fun computeIpChecksum(packet: ByteArray) {
        packet[10] = 0; packet[11] = 0
        var sum = 0L
        for (i in 0 until 20 step 2) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val checksum = sum.toInt().inv() and 0xFFFF
        packet[10] = ((checksum shr 8) and 0xFF).toByte()
        packet[11] = (checksum and 0xFF).toByte()
    }

    private fun computeTcpChecksum(packet: ByteArray, tcpOffset: Int, tcpTotalLen: Int) {
        packet[tcpOffset + 16] = 0; packet[tcpOffset + 17] = 0
        var sum = 0L
        // pseudo header
        for (i in 12 until 20 step 2) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        sum += PacketParser.PROTOCOL_TCP
        sum += tcpTotalLen
        // TCP segment
        var i = tcpOffset
        while (i < tcpOffset + tcpTotalLen - 1) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < tcpOffset + tcpTotalLen) {
            sum += (packet[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val checksum = sum.toInt().inv() and 0xFFFF
        packet[tcpOffset + 16] = ((checksum shr 8) and 0xFF).toByte()
        packet[tcpOffset + 17] = (checksum and 0xFF).toByte()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "弱网模拟服务", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setContentTitle("WeakNet 弱网模拟中")
            .setContentText(profileFlow.value.name)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }
}

object TcpFlags {
    const val FIN = 0x01
    const val SYN = 0x02
    const val RST = 0x04
    const val PSH = 0x08
    const val ACK = 0x10
}
