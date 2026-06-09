package com.weaknet.simulator.ui

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weaknet.simulator.ui.screen.MainScreen
import com.weaknet.simulator.ui.screen.SplashScreen
import com.weaknet.simulator.ui.theme.WeakNetTheme

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
                    val vm: MainViewModel = viewModel()
                    val isRunning by vm.isRunning.collectAsState()
                    val profile by vm.currentProfile.collectAsState()
                    val stats by vm.stats.collectAsState()
                    val selectedApps by vm.selectedApps.collectAsState()
                    val apps by vm.installedApps.collectAsState()

                    MainScreen(
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
            }
        }
    }
}
