package com.weaknet.simulator.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weaknet.simulator.network.DnsResult
import com.weaknet.simulator.network.PingResult
import com.weaknet.simulator.ui.DnsState
import com.weaknet.simulator.ui.NetworkInfo
import com.weaknet.simulator.ui.PingState

@Composable
fun DiagnosticsScreen(
    modifier: Modifier = Modifier,
    networkInfo: NetworkInfo,
    pingState: PingState,
    dnsState: DnsState,
    onRefreshNetwork: () -> Unit,
    onRunPing: (String, Int) -> Unit,
    onRunDns: (String, String?) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { NetworkInfoCard(networkInfo, onRefreshNetwork) }
        item { PingCard(pingState, onRunPing) }
        item { DnsCard(dnsState, onRunDns) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun NetworkInfoCard(info: NetworkInfo, onRefresh: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("网络信息", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }

            Spacer(Modifier.height(12.dp))

            InfoRow("网络类型", info.type)
            info.wifiSsid?.let { InfoRow("WiFi 名称", it) }
            InfoRow("公网 IP", info.publicIp)
            InfoRow("内网 IP", info.localIp)
            InfoRow("运营商", info.carrier)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = Color.Gray)
        Text(value, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun PingCard(state: PingState, onRun: (String, Int) -> Unit) {
    var host by remember { mutableStateOf("8.8.8.8") }
    var count by remember { mutableStateOf("4") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NetworkPing, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Ping 测试", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("主机/IP") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = count,
                    onValueChange = { count = it },
                    label = { Text("次数") },
                    singleLine = true,
                    modifier = Modifier.width(72.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { onRun(host.trim(), count.toIntOrNull() ?: 4) },
                enabled = !state.isRunning && host.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isRunning) {
                    Text("⏳ 测试中...")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("开始 Ping")
                }
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            state.result?.let { r ->
                Spacer(Modifier.height(12.dp))
                PingResultView(r)
            }

            if (state.history.size >= 2) {
                Spacer(Modifier.height(12.dp))
                Text("延迟趋势", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                LatencyChart(state.history)
            }
        }
    }
}

@Composable
private fun PingResultView(r: PingResult) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${r.host}", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                PingMetric("最小", "${r.minMs}ms")
                PingMetric("平均", "${r.avgMs}ms")
                PingMetric("最大", "${r.maxMs}ms")
                PingMetric("丢包", "${r.lossPercent}%")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "发送 ${r.packetsTransmitted} / 接收 ${r.packetsReceived}",
                fontSize = 12.sp, color = Color.Gray
            )
        }
    }
}

@Composable
private fun PingMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
        Text(label, fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun LatencyChart(history: List<Float>) {
    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
        if (history.size < 2) return@Canvas

        val maxVal = (history.max() * 1.2f).coerceAtLeast(1f)
        val minVal = 0f
        val range = maxVal - minVal

        val stepX = size.width / (history.size - 1)
        val path = Path()

        history.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - minVal) / range) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path, color = lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))

        history.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - minVal) / range) * size.height
            drawCircle(color = lineColor, radius = 4f, center = Offset(x, y))
        }
    }
}

@Composable
private fun DnsCard(state: DnsState, onRun: (String, String?) -> Unit) {
    var host by remember { mutableStateOf("google.com") }
    var dnsServer by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val presetDns = listOf(
        "" to "系统默认",
        "8.8.8.8" to "Google (8.8.8.8)",
        "1.1.1.1" to "Cloudflare (1.1.1.1)",
        "208.67.222.222" to "OpenDNS"
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("DNS 查询", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("域名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Box {
                OutlinedTextField(
                    value = if (dnsServer.isBlank()) "系统默认" else dnsServer,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("DNS 服务器") },
                    trailingIcon = {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    presetDns.forEach { (server, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { dnsServer = server; expanded = false }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { onRun(host.trim(), dnsServer.takeIf { it.isNotBlank() }) },
                enabled = !state.isRunning && host.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isRunning) {
                    Text("⏳ 查询中...")
                } else {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("查询 DNS")
                }
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            state.result?.let { r ->
                Spacer(Modifier.height(12.dp))
                DnsResultView(r)
            }
        }
    }
}

@Composable
private fun DnsResultView(r: DnsResult) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(r.host, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text("${r.elapsedMs}ms", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
            }
            if (r.dnsServer != null) {
                Text("via ${r.dnsServer}", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(8.dp))
            r.addresses.forEach { ip ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Circle, contentDescription = null, modifier = Modifier.size(8.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(ip, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
