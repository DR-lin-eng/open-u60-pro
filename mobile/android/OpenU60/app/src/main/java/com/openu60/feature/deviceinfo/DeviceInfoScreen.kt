package com.openu60.feature.deviceinfo

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    onBack: () -> Unit,
    viewModel: DeviceInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备信息") },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.error != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(
                            state.error!!,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                InfoSection("设备") {
                    InfoRow("IMEI", state.identity.imei)
                }

                InfoSection("SIM") {
                    InfoRow("状态", state.identity.simStatus)
                    InfoRow("ICCID", state.identity.simICCID)
                    InfoRow("IMSI", state.identity.simIMSI)
                    InfoRow("MSISDN", state.identity.msisdn)
                    InfoRow("运营商", state.identity.spn)
                    InfoRow("MCC/MNC", "${state.identity.mcc}/${state.identity.mnc}")
                }

                InfoSection("网络") {
                    InfoRow("WAN IPv4", state.identity.wanIPv4)
                    if (state.identity.wanIPv6.isEmpty()) {
                        InfoRow("WAN IPv6", "")
                    } else {
                        state.identity.wanIPv6.forEachIndexed { index, ipv6 ->
                            InfoRow("WAN IPv6 ${index + 1}", ipv6)
                        }
                    }
                    InfoRow("LAN IP", state.identity.lanIP)
                }
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        SelectionContainer {
            Text(
                value.ifBlank { "--" },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
