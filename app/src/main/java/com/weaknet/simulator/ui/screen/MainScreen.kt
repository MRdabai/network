package com.weaknet.simulator.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weaknet.simulator.model.NetworkProfile
import com.weaknet.simulator.model.TrafficStats
import com.weaknet.simulator.ui.AppInfo

@Composable
fun MainScreen(
    isRunning: Boolean,
    currentProfile: NetworkProfile,
    stats: TrafficStats,
    selectedApps: Set<String>,
    installedApps: List<AppInfo>,
    onProfileSelected: (NetworkProfile) -> Unit,
    onCustomUpdate: (Int, Int, Float, Long, Long) -> Unit,
    onToggleVpn: () -> Unit,
    onToggleApp: (String) -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    var showAppSelector by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 状态卡片
        item {
            StatusCard(isRunning, currentProfile, onToggleVpn)
        }

        // 实时统计
        item {
            StatsCard(stats, isRunning)
        }

        // 预设场景
        item {
            Text("网络场景", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        item {
            ProfileGrid(currentProfile, onProfileSelected)
        }

        // 自定义参数
        item {
            OutlinedButton(
                onClick = { showCustomDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("自定义参数")
            }
        }

        // 应用过滤
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("应用过滤", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                TextButton(onClick = { showAppSelector = true }) {
                    Text("选择应用(${selectedApps.size})")
                }
            }
        }
        item {
            if (selectedApps.isEmpty()) {
                Text("未选择应用 = 全局生效", color = Color.Gray, fontSize = 14.sp)
            } else {
                Text("仅对 ${selectedApps.size} 个应用生效", fontSize = 14.sp)
            }
        }
    }

    if (showCustomDialog) {
        CustomProfileDialog(
            currentProfile = currentProfile,
            onDismiss = { showCustomDialog = false },
            onConfirm = { latency, jitter, loss, up, down ->
                onCustomUpdate(latency, jitter, loss, up, down)
                showCustomDialog = false
            }
        )
    }

    if (showAppSelector) {
        AppSelectorDialog(
            apps = installedApps,
            selectedApps = selectedApps,
            onToggle = onToggleApp,
            onDismiss = { showAppSelector = false }
        )
    }
}

@Composable
fun StatusCard(isRunning: Boolean, profile: NetworkProfile, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    if (isRunning) "弱网模拟中" else "未启动",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                if (isRunning) {
                    Spacer(Modifier.height(4.dp))
                    Text("当前: ${profile.name}", fontSize = 14.sp)
                    Text(
                        "延迟${profile.latencyMs}ms 丢包${(profile.lossRate * 100).toInt()}%",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
            }
            FilledIconButton(
                onClick = onToggle,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
fun StatsCard(stats: TrafficStats, isRunning: Boolean) {
    if (!isRunning) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("实时统计", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatItem("上行", "${stats.uplinkPackets}包")
                StatItem("下行", "${stats.downlinkPackets}包")
                StatItem("丢弃", "${stats.droppedPackets}包")
                StatItem("延迟", "${stats.delayedPackets}包")
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileGrid(currentProfile: NetworkProfile, onSelect: (NetworkProfile) -> Unit) {
    val grouped = NetworkProfile.GROUPED
    var infoCategory by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        grouped.forEach { (category, profiles) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.Info,
                    contentDescription = "查看说明",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { infoCategory = category },
                    tint = MaterialTheme.colorScheme.outline
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                profiles.forEach { profile ->
                    val isSelected = profile.name == currentProfile.name
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelect(profile) },
                        label = { Text(profile.name, fontSize = 13.sp) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }
    }

    infoCategory?.let { cat ->
        val profiles = grouped[cat] ?: emptyList()
        AlertDialog(
            onDismissRequest = { infoCategory = null },
            title = { Text("$cat 场景说明") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    profiles.forEach { p ->
                        Column {
                            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            val lossPercent = (p.lossRate * 100).toInt()
                            val bandwidth = if (p.downlinkKbps <= 0) "不限速"
                                else if (p.downlinkKbps < 1024) "${p.downlinkKbps}kbps"
                                else "${p.downlinkKbps / 1024}Mbps"
                            Text(
                                "延迟${p.latencyMs}ms  抖动${p.jitterMs}ms  丢包${lossPercent}%  $bandwidth",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { infoCategory = null }) { Text("关闭") }
            }
        )
    }
}

@Composable
fun CustomProfileDialog(
    currentProfile: NetworkProfile,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Float, Long, Long) -> Unit
) {
    var latency by remember { mutableStateOf(currentProfile.latencyMs.toString()) }
    var jitter by remember { mutableStateOf(currentProfile.jitterMs.toString()) }
    var loss by remember { mutableStateOf((currentProfile.lossRate * 100).toInt().toString()) }
    var uplink by remember { mutableStateOf(currentProfile.uplinkKbps.toString()) }
    var downlink by remember { mutableStateOf(currentProfile.downlinkKbps.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义网络参数") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = latency, onValueChange = { latency = it },
                    label = { Text("延迟 (ms)") }, singleLine = true
                )
                OutlinedTextField(
                    value = jitter, onValueChange = { jitter = it },
                    label = { Text("抖动 (ms)") }, singleLine = true
                )
                OutlinedTextField(
                    value = loss, onValueChange = { loss = it },
                    label = { Text("丢包率 (%)") }, singleLine = true
                )
                OutlinedTextField(
                    value = uplink, onValueChange = { uplink = it },
                    label = { Text("上行限速 (KB/s, 0=不限)") }, singleLine = true
                )
                OutlinedTextField(
                    value = downlink, onValueChange = { downlink = it },
                    label = { Text("下行限速 (KB/s, 0=不限)") }, singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    latency.toIntOrNull() ?: 0,
                    jitter.toIntOrNull() ?: 0,
                    (loss.toIntOrNull() ?: 0) / 100f,
                    uplink.toLongOrNull() ?: 0,
                    downlink.toLongOrNull() ?: 0
                )
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun AppSelectorDialog(
    apps: List<AppInfo>,
    selectedApps: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showSystem by remember { mutableStateOf(false) }
    val filteredApps = remember(apps, showSystem) {
        if (showSystem) apps else apps.filter { !it.isSystem }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("选择应用")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("系统应用", fontSize = 12.sp)
                    Switch(checked = showSystem, onCheckedChange = { showSystem = it })
                }
            }
        },
        text = {
            LazyColumn(modifier = Modifier.height(400.dp)) {
                items(filteredApps) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(app.packageName) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedApps.contains(app.packageName),
                            onCheckedChange = { onToggle(app.packageName) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(app.label, fontSize = 14.sp)
                            Text(app.packageName, fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}
