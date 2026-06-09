package com.weaknet.simulator.ui

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weaknet.simulator.ui.screen.DiagnosticsScreen
import com.weaknet.simulator.ui.screen.MainScreen
import com.weaknet.simulator.ui.screen.SplashScreen
import com.weaknet.simulator.ui.theme.WeakNetTheme

private enum class Tab(val label: String, val icon: ImageVector) {
    SIMULATION("弱网模拟", Icons.Default.SignalCellularAlt),
    DIAGNOSTICS("网络诊断", Icons.Default.NetworkCheck)
}

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingViewModel?.startVpn()
        }
    }

    private var pendingViewModel: MainViewModel? = null
    private var showSplash by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            WeakNetTheme {
                if (showSplash) {
                    SplashScreen(onFinished = { showSplash = false })
                } else {
                    MainApp()
                }
            }
        }
    }

    @Composable
    private fun MainApp() {
        var selectedTab by remember { mutableStateOf(Tab.SIMULATION) }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            when (selectedTab) {
                Tab.SIMULATION -> {
                    val vm: MainViewModel = viewModel()
                    val isRunning by vm.isRunning.collectAsState()
                    val profile by vm.currentProfile.collectAsState()
                    val stats by vm.stats.collectAsState()
                    val selectedApps by vm.selectedApps.collectAsState()
                    val apps by vm.installedApps.collectAsState()

                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        isRunning = isRunning,
                        currentProfile = profile,
                        stats = stats,
                        selectedApps = selectedApps,
                        installedApps = apps,
                        onProfileSelected = { vm.selectProfile(it) },
                        onCustomUpdate = { latency, jitter, loss, up, down ->
                            vm.updateCustomProfile(latency, jitter, loss, up, down)
                        },
                        onToggleVpn = {
                            if (isRunning) {
                                vm.stopVpn()
                            } else {
                                val prepareIntent = vm.prepareVpn()
                                if (prepareIntent != null) {
                                    pendingViewModel = vm
                                    vpnPermissionLauncher.launch(prepareIntent)
                                } else {
                                    vm.startVpn()
                                }
                            }
                        },
                        onToggleApp = { vm.toggleApp(it) }
                    )
                }
                Tab.DIAGNOSTICS -> {
                    val diagVm: DiagnosticsViewModel = viewModel()
                    val networkInfo by diagVm.networkInfo.collectAsState()
                    val pingState by diagVm.pingState.collectAsState()
                    val dnsState by diagVm.dnsState.collectAsState()

                    DiagnosticsScreen(
                        modifier = Modifier.padding(innerPadding),
                        networkInfo = networkInfo,
                        pingState = pingState,
                        dnsState = dnsState,
                        onRefreshNetwork = { diagVm.refreshNetworkInfo() },
                        onRunPing = { host, count -> diagVm.runPing(host, count) },
                        onRunDns = { host, server -> diagVm.runDns(host, server) }
                    )
                }
            }
        }
    }
}
