package com.weaknet.simulator.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.weaknet.simulator.ui.scan.QrScanActivity

@Composable
fun QrScanLauncher(
    onResult: (String?) -> Unit
) {
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result: ScanIntentResult ->
        onResult(result.contents)
    }

    LaunchedEffect(Unit) {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("")
            setBeepEnabled(false)
            setOrientationLocked(true)
            setCameraId(0)
            captureActivity = QrScanActivity::class.java
        }
        scanLauncher.launch(options)
    }
}
