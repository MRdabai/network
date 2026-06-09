package com.weaknet.simulator.ui

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weaknet.simulator.model.NetworkProfile
import com.weaknet.simulator.model.TrafficStats
import com.weaknet.simulator.vpn.WeakNetVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val currentProfile: StateFlow<NetworkProfile> = WeakNetVpnService.profileFlow
    val isRunning: StateFlow<Boolean> = WeakNetVpnService.runningFlow

    private val _selectedApps = MutableStateFlow<Set<String>>(emptySet())
    val selectedApps: StateFlow<Set<String>> = _selectedApps

    private val _customProfile = MutableStateFlow(NetworkProfile.NORMAL)
    val customProfile: StateFlow<NetworkProfile> = _customProfile

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps

    val stats: StateFlow<TrafficStats> get() = WeakNetVpnService.statsFlow

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _installedApps.value = loadInstalledApps()
        }
    }

    fun selectProfile(profile: NetworkProfile) {
        WeakNetVpnService.updateProfile(profile)
    }

    fun updateCustomProfile(
        latencyMs: Int? = null,
        jitterMs: Int? = null,
        lossRate: Float? = null,
        uplinkKbps: Long? = null,
        downlinkKbps: Long? = null
    ) {
        val current = _customProfile.value
        val updated = current.copy(
            name = "自定义",
            latencyMs = latencyMs ?: current.latencyMs,
            jitterMs = jitterMs ?: current.jitterMs,
            lossRate = lossRate ?: current.lossRate,
            uplinkKbps = uplinkKbps ?: current.uplinkKbps,
            downlinkKbps = downlinkKbps ?: current.downlinkKbps
        )
        _customProfile.value = updated
        WeakNetVpnService.updateProfile(updated)
    }

    fun toggleApp(packageName: String) {
        val current = _selectedApps.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _selectedApps.value = current
        WeakNetVpnService.updateTargetApps(current)
        if (isRunning.value) {
            restartVpn()
        }
    }

    private fun restartVpn() {
        stopVpn()
        val context = getApplication<Application>()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val intent = Intent(context, WeakNetVpnService::class.java)
            context.startForegroundService(intent)
        }, 500)
    }

    private fun loadInstalledApps(): List<AppInfo> {
        val pm = getApplication<Application>().packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return packages.map { info ->
            AppInfo(
                packageName = info.packageName,
                label = info.loadLabel(pm).toString(),
                isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }.sortedWith(compareBy({ it.isSystem }, { it.label }))
    }

    fun startVpn() {
        val context = getApplication<Application>()
        val intent = Intent(context, WeakNetVpnService::class.java)
        context.startForegroundService(intent)
    }

    fun stopVpn() {
        val context = getApplication<Application>()
        val intent = Intent(context, WeakNetVpnService::class.java).apply {
            action = "STOP"
        }
        context.startService(intent)
    }

    fun prepareVpn(): Intent? {
        return VpnService.prepare(getApplication())
    }
}
