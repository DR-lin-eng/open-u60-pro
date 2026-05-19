package com.openu60.feature.router.mobilenetwork

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileNetworkScreen(
    onBack: () -> Unit,
    viewModel: MobileNetworkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var pendingConfirmation by remember { mutableStateOf<MobileNetworkConfirmation?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    pendingConfirmation?.let { confirmation ->
        AlertDialog(
            onDismissRequest = { pendingConfirmation = null },
            title = { Text(confirmation.title) },
            text = { Text(confirmation.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (val action = pendingConfirmation) {
                            is MobileNetworkConfirmation.AirplaneMode ->
                                viewModel.setAirplaneMode(action.enabled)
                            is MobileNetworkConfirmation.MobileData ->
                                viewModel.setMobileData(action.enabled)
                            null -> Unit
                        }
                        pendingConfirmation = null
                    },
                ) {
                    Text(confirmation.confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmation = null }) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("移动网络") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.message?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.messageIsError) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Text(
                            msg,
                            modifier = Modifier.padding(16.dp),
                            color = if (state.messageIsError) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                if (state.showRebootAfterAirplaneOff) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        Text(
                            "调制解调器在退出飞行模式后可能无法自行恢复，通常需要完整重启。这是已知的固件限制。",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                // Toggles
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("连接", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("飞行模式")
                            Switch(
                                checked = state.airplaneModeEnabled,
                                onCheckedChange = {
                                    pendingConfirmation = MobileNetworkConfirmation.AirplaneMode(it)
                                },
                                enabled = !state.isLoading,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("移动数据")
                            Switch(
                                checked = state.config.isDataEnabled,
                                onCheckedChange = {
                                    pendingConfirmation = MobileNetworkConfirmation.MobileData(it)
                                },
                                enabled = !state.isLoading && !state.airplaneModeEnabled,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("漫游")
                            Switch(
                                checked = state.config.isRoamingEnabled,
                                onCheckedChange = {},
                                enabled = false,
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "状态：${state.config.connectStatus.ifBlank { "--" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Network scan
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("网络扫描", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { viewModel.scanNetworks() },
                            enabled = !state.isScanning && !state.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("扫描中...")
                            } else {
                                Text("扫描可用网络")
                            }
                        }

                        if (state.config.operators.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            state.config.operators.forEach { op ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                    onClick = { viewModel.registerNetwork(op) },
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column {
                                            Text(op.name.ifBlank { op.mccMnc }, fontWeight = FontWeight.Medium)
                                            Text(
                                                "${op.mccMnc} - ${op.rat}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                            )
                                        }
                                        Text(
                                            op.status,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface MobileNetworkConfirmation {
    val title: String
    val message: String
    val confirmLabel: String

    data class AirplaneMode(val enabled: Boolean) : MobileNetworkConfirmation {
        override val title: String = if (enabled) "开启飞行模式？" else "关闭飞行模式？"
        override val message: String = if (enabled) {
            "开启后会立即断开蜂窝网络与移动数据，当前联网状态可能中断。"
        } else {
            "关闭后设备会尝试恢复蜂窝联网。根据当前固件限制，可能仍需重启才能完全恢复。"
        }
        override val confirmLabel: String = if (enabled) "继续开启" else "继续关闭"
    }

    data class MobileData(val enabled: Boolean) : MobileNetworkConfirmation {
        override val title: String = if (enabled) "开启移动数据？" else "关闭移动数据？"
        override val message: String = if (enabled) {
            "开启后设备会尝试重新建立蜂窝连接，网络状态可能在数秒内波动。"
        } else {
            "关闭后会立即断开当前蜂窝数据连接，依赖移动网络的访问会中断。"
        }
        override val confirmLabel: String = if (enabled) "继续开启" else "继续关闭"
    }
}
