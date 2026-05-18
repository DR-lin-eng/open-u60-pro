package com.openu60.feature.router.device

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControlScreen(
    onBack: () -> Unit,
    viewModel: DeviceControlViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    // Reboot confirmation dialog
    if (state.showRebootConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRebootConfirm() },
            title = { Text("重启设备") },
            text = { Text("确定要重启设备吗？") },
            confirmButton = {
                TextButton(onClick = { viewModel.reboot() }) {
                    Text("重启", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRebootConfirm() }) { Text("取消") }
            },
        )
    }

    // Factory reset confirmation dialog
    if (state.showResetConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissResetConfirm() },
            title = { Text("恢复出厂设置") },
            text = { Text("这将清除所有设置并将设备恢复到出厂默认状态，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = { viewModel.factoryReset() }) {
                    Text("重置", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissResetConfirm() }) { Text("取消") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备控制") },
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

                // Charge Limit
                if (state.chargeControlLoaded) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("充电上限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))

                            ToggleRow("充电上限", state.chargeLimitEnabled) {
                                viewModel.setChargeLimit(it, state.chargeLimit)
                            }

                            if (state.chargeLimitEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                var sliderValue by remember(state.chargeLimit) { mutableFloatStateOf(state.chargeLimit.toFloat()) }
                                Text(
                                    "在 ${sliderValue.toInt()}% 停止",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Slider(
                                    value = sliderValue,
                                    onValueChange = { sliderValue = it },
                                    onValueChangeFinished = { viewModel.setChargeLimit(true, sliderValue.toInt()) },
                                    valueRange = 50f..100f,
                                    steps = 9,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "恢复间隔：${state.hysteresis}%",
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                val newVal = (state.hysteresis - 1).coerceAtLeast(1)
                                                viewModel.setChargeLimit(true, state.chargeLimit, newVal)
                                            },
                                            enabled = state.hysteresis > 1,
                                        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                                        IconButton(
                                            onClick = {
                                                val newVal = (state.hysteresis + 1).coerceAtMost(20)
                                                viewModel.setChargeLimit(true, state.chargeLimit, newVal)
                                            },
                                            enabled = state.hysteresis < 20,
                                        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            if (state.chargeLimitEnabled) {
                                Text(
                                    "充电会在 ${state.chargeLimit}% 时停止，并在 ${state.chargeLimit - state.hysteresis}% 时恢复。\n\n" +
                                        "恢复间隔可以避免充电器频繁启停。较小的间隔会让电量更接近目标值，但切换更频繁；" +
                                        "较大的间隔则会减少循环次数，但电量波动更明显。\n\n默认值：5%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    "电量达到设定值时停止充电，可延长电池寿命。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Other toggles
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("电源设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (state.powerSaveLoaded) {
                            ToggleRow("省电模式", state.powerSave) { viewModel.togglePowerSave(it) }
                        }
                        if (state.fastBootLoaded) {
                            ToggleRow("快速启动", state.fastBoot) { viewModel.toggleFastBoot(it) }
                        }
                    }
                }

                // Reboot & Reset
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("设备操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.showRebootConfirm() },
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("重启设备") }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.showResetConfirm() },
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("恢复出厂设置") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
