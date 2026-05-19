package com.openu60.feature.router.wifi

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openu60.core.model.WiFiConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WiFiSettingsScreen(
    onBack: () -> Unit,
    viewModel: WiFiSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showApplyConfirmation by remember { mutableStateOf(false) }
    val disruptiveMessages = remember(state.savedConfig, state.config) {
        buildWiFiImpactMessages(state.savedConfig, state.config)
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    if (showApplyConfirmation) {
        AlertDialog(
            onDismissRequest = { showApplyConfirmation = false },
            title = { Text("应用 Wi‑Fi 变更？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("这些开关会影响当前无线连接：")
                    disruptiveMessages.forEach { msg -> Text("• $msg") }
                    Text("应用后 Wi‑Fi 服务可能短暂重启；如果你正通过 Wi‑Fi 管理设备，连接可能中断。")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showApplyConfirmation = false
                        viewModel.save()
                    },
                ) {
                    Text("继续应用")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApplyConfirmation = false }) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wi-Fi 设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (disruptiveMessages.isNotEmpty()) {
                                showApplyConfirmation = true
                            } else {
                                viewModel.save()
                            }
                        },
                        enabled = !state.isLoading,
                    ) {
                        Text("保存")
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

                // Global toggle
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Wi-Fi", fontWeight = FontWeight.Bold)
                        Switch(
                            checked = state.config.wifiOnOff,
                            onCheckedChange = { viewModel.updateConfig(state.config.copy(wifiOnOff = it)) },
                        )
                    }
                }

                // 2.4GHz
                BandCard(
                    title = "2.4 GHz",
                    ssid = state.config.ssid2g,
                    password = state.config.key2g,
                    channel = state.config.channel2g,
                    bandwidth = state.config.bandwidth2g,
                    txpower = state.config.txpower2g,
                    encryption = state.config.encryption2g,
                    hidden = state.config.hidden2g,
                    enabled = !state.config.radio2gDisabled,
                    channelOptions = WiFiConfig.channelOptions2g,
                    bandwidthOptions = WiFiConfig.bandwidthOptions2g,
                    onSsidChange = { viewModel.updateConfig(state.config.copy(ssid2g = it)) },
                    onPasswordChange = { viewModel.updateConfig(state.config.copy(key2g = it)) },
                    onChannelChange = { viewModel.updateConfig(state.config.copy(channel2g = it)) },
                    onBandwidthChange = { viewModel.updateConfig(state.config.copy(bandwidth2g = it)) },
                    onTxpowerChange = { viewModel.updateConfig(state.config.copy(txpower2g = it)) },
                    onEncryptionChange = { viewModel.updateConfig(state.config.copy(encryption2g = it)) },
                    onHiddenChange = { viewModel.updateConfig(state.config.copy(hidden2g = it)) },
                    onEnabledChange = { viewModel.updateConfig(state.config.copy(radio2gDisabled = !it)) },
                )

                // 5GHz
                val available5gChannels = WiFiConfig.channels5g(state.config.bandwidth5g)
                val available5gBandwidths = WiFiConfig.bandwidths5g(state.config.channel5g)
                BandCard(
                    title = "5 GHz",
                    ssid = state.config.ssid5g,
                    password = state.config.key5g,
                    channel = state.config.channel5g,
                    bandwidth = state.config.bandwidth5g,
                    txpower = state.config.txpower5g,
                    encryption = state.config.encryption5g,
                    hidden = state.config.hidden5g,
                    enabled = !state.config.radio5gDisabled,
                    channelOptions = available5gChannels,
                    bandwidthOptions = available5gBandwidths,
                    onSsidChange = { viewModel.updateConfig(state.config.copy(ssid5g = it)) },
                    onPasswordChange = { viewModel.updateConfig(state.config.copy(key5g = it)) },
                    onChannelChange = {
                        var newConfig = state.config.copy(channel5g = it)
                        val validBw = WiFiConfig.bandwidths5g(it)
                        if (newConfig.bandwidth5g !in validBw) newConfig = newConfig.copy(bandwidth5g = "auto")
                        viewModel.updateConfig(newConfig)
                    },
                    onBandwidthChange = {
                        var newConfig = state.config.copy(bandwidth5g = it)
                        val validCh = WiFiConfig.channels5g(it)
                        if (newConfig.channel5g !in validCh) newConfig = newConfig.copy(channel5g = "auto")
                        viewModel.updateConfig(newConfig)
                    },
                    onTxpowerChange = { viewModel.updateConfig(state.config.copy(txpower5g = it)) },
                    onEncryptionChange = { viewModel.updateConfig(state.config.copy(encryption5g = it)) },
                    onHiddenChange = { viewModel.updateConfig(state.config.copy(hidden5g = it)) },
                    onEnabledChange = { viewModel.updateConfig(state.config.copy(radio5gDisabled = !it)) },
                )
            }
        }
    }
}

private fun buildWiFiImpactMessages(saved: WiFiConfig, current: WiFiConfig): List<String> {
    val messages = mutableListOf<String>()
    if (saved.wifiOnOff != current.wifiOnOff) {
        messages += if (current.wifiOnOff) {
            "将开启 Wi‑Fi 总开关，设备会重新广播无线网络。"
        } else {
            "将关闭 Wi‑Fi 总开关，当前无线连接会立即中断。"
        }
    }
    if (saved.radio2gDisabled != current.radio2gDisabled) {
        messages += if (current.radio2gDisabled) {
            "将关闭 2.4 GHz，无线客户端可能失去该频段连接。"
        } else {
            "将开启 2.4 GHz，设备会重新启用该频段。"
        }
    }
    if (saved.radio5gDisabled != current.radio5gDisabled) {
        messages += if (current.radio5gDisabled) {
            "将关闭 5 GHz，无线客户端可能失去该频段连接。"
        } else {
            "将开启 5 GHz，设备会重新启用该频段。"
        }
    }
    return messages
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BandCard(
    title: String,
    ssid: String,
    password: String,
    channel: String,
    bandwidth: String,
    txpower: String,
    encryption: String,
    hidden: Boolean,
    enabled: Boolean,
    channelOptions: List<String>,
    bandwidthOptions: List<String>,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onChannelChange: (String) -> Unit,
    onBandwidthChange: (String) -> Unit,
    onTxpowerChange: (String) -> Unit,
    onEncryptionChange: (String) -> Unit,
    onHiddenChange: (Boolean) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("已开启", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(checked = enabled, onCheckedChange = onEnabledChange)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (enabled) {
                OutlinedTextField(value = ssid, onValueChange = onSsidChange, label = { Text("SSID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = password, onValueChange = onPasswordChange, label = { Text("密码") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector("信道", channel, channelOptions, onChannelChange, Modifier.weight(1f))
                    DropdownSelector("带宽", bandwidth, bandwidthOptions, onBandwidthChange, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector("加密", encryption, WiFiConfig.encryptionOptions, onEncryptionChange, Modifier.weight(1f))
                    DropdownSelector("发射功率", txpower, WiFiConfig.txpowerOptions, onTxpowerChange, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("隐藏网络")
                    Switch(checked = hidden, onCheckedChange = onHiddenChange)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
