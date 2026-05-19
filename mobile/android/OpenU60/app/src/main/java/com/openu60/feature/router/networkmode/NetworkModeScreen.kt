package com.openu60.feature.router.networkmode

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
import com.openu60.core.model.NetworkModeConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkModeScreen(
    onBack: () -> Unit,
    viewModel: NetworkModeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var pendingMode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    pendingMode?.let { mode ->
        val modeLabel = networkModeLabel(mode)
        AlertDialog(
            onDismissRequest = { pendingMode = null },
            title = { Text("切换网络模式？") },
            text = {
                Text("切换到 $modeLabel 后，设备可能会短暂掉线并重新注册网络。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setNetworkMode(mode)
                        pendingMode = null
                    },
                ) {
                    Text("继续切换")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMode = null }) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("网络模式") },
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

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("选择网络模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))

                        NetworkModeConfig.netSelectOptions.forEach { (label, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = state.config.netSelect == value,
                                    onClick = {
                                        if (state.config.netSelect != value) {
                                            pendingMode = value
                                        }
                                    },
                                    enabled = !state.isLoading,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun networkModeLabel(value: String): String {
    return NetworkModeConfig.netSelectOptions.firstOrNull { it.second == value }?.first ?: value
}
