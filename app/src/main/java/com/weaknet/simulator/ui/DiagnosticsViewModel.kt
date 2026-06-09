package com.weaknet.simulator.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weaknet.simulator.network.DnsResult
import com.weaknet.simulator.network.DnsTool
import com.weaknet.simulator.network.PingResult
import com.weaknet.simulator.network.PingTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

data class NetworkInfo(
    val type: String = "检测中...",
    val wifiSsid: String? = null,
    val publicIp: String = "获取中...",
    val localIp: String = "-",
    val carrier: String = "-"
)

data class PingState(
    val isRunning: Boolean = false,
    val result: PingResult? = null,
    val error: String? = null,
    val history: List<Float> = emptyList()
)

data class DnsState(
    val isRunning: Boolean = false,
    val result: DnsResult? = null,
    val error: String? = null
)

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {

    private val _networkInfo = MutableStateFlow(NetworkInfo())
    val networkInfo: StateFlow<NetworkInfo> = _networkInfo

    private val _pingState = MutableStateFlow(PingState())
    val pingState: StateFlow<PingState> = _pingState

    private val _dnsState = MutableStateFlow(DnsState())
    val dnsState: StateFlow<DnsState> = _dnsState

    init {
        refreshNetworkInfo()
    }

    fun refreshNetworkInfo() {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

                val network = cm.activeNetwork
                val caps = network?.let { cm.getNetworkCapabilities(it) }

                val type = when {
                    caps == null -> "无网络"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> getCellularType(ctx)
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
                    else -> "其他"
                }

                val ssid = if (type == "WiFi") getWifiSsid(ctx) else null
                val localIp = getLocalIp()
                val carrier = getCarrier(ctx)

                _networkInfo.value = NetworkInfo(
                    type = type,
                    wifiSsid = ssid,
                    publicIp = "获取中...",
                    localIp = localIp,
                    carrier = carrier
                )

                launch(Dispatchers.IO) {
                    val publicIp = fetchPublicIp()
                    _networkInfo.value = _networkInfo.value.copy(publicIp = publicIp)
                }
            } catch (e: Exception) {
                _networkInfo.value = NetworkInfo(type = "检测失败")
            }
        }
    }

    private fun fetchPublicIp(): String {
        return try {
            val conn = URL("https://api.ipify.org").openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            val result = conn.inputStream.bufferedReader().readText().trim()
            conn.disconnect()
            result
        } catch (_: Exception) {
            try {
                val conn = URL("https://ifconfig.me/ip").openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                val result = conn.inputStream.bufferedReader().readText().trim()
                conn.disconnect()
                result
            } catch (_: Exception) {
                "获取失败"
            }
        }
    }

    fun runPing(host: String, count: Int = 4) {
        if (_pingState.value.isRunning) return
        _pingState.value = PingState(isRunning = true)
        viewModelScope.launch {
            try {
                val result = PingTool.ping(host, count)
                result.onSuccess { r ->
                    val history = _pingState.value.history.toMutableList()
                    history.add(r.avgMs)
                    if (history.size > 10) history.removeAt(0)
                    _pingState.value = PingState(result = r, history = history)
                }.onFailure { e ->
                    _pingState.value = PingState(error = e.message ?: "Ping 失败")
                }
            } catch (e: Exception) {
                _pingState.value = PingState(error = "Ping 异常: ${e.message}")
            }
        }
    }

    fun runDns(host: String, dnsServer: String? = null) {
        if (_dnsState.value.isRunning) return
        _dnsState.value = DnsState(isRunning = true)
        viewModelScope.launch {
            try {
                val server = dnsServer?.takeIf { it.isNotBlank() }
                val result = DnsTool.lookup(host, server)
                result.onSuccess { r ->
                    _dnsState.value = DnsState(result = r)
                }.onFailure { e ->
                    _dnsState.value = DnsState(error = e.message ?: "DNS 查询失败")
                }
            } catch (e: Exception) {
                _dnsState.value = DnsState(error = "DNS 异常: ${e.message}")
            }
        }
    }

    private fun getCellularType(ctx: Context): String {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return try {
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                else -> "移动网络"
            }
        } catch (_: SecurityException) {
            "移动网络"
        }
    }

    @Suppress("DEPRECATION")
    private fun getWifiSsid(ctx: Context): String? {
        return try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            val ssid = info.ssid?.removeSurrounding("\"")
            if (ssid == "<unknown ssid>") null else ssid
        } catch (_: Exception) {
            null
        }
    }

    private fun getLocalIp(): String {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress ?: "-"
        } catch (_: Exception) {
            "-"
        }
    }

    private fun getCarrier(ctx: Context): String {
        return try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.networkOperatorName.ifBlank { "-" }
        } catch (_: Exception) {
            "-"
        }
    }
}
