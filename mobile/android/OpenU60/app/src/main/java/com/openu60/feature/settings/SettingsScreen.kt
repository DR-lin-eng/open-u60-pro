package com.openu60.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openu60.core.network.AuthState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToLogin: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsState()
    val gateway by viewModel.gateway.collectAsState()
    val pollInterval by viewModel.pollInterval.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val context = LocalContext.current
    val versionLabel = remember(context) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: "0.0.0"
        val versionCode = packageInfo.longVersionCode
        "OpenU60 v$versionName ($versionCode)"
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Connection section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "连接",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = gateway,
                        onValueChange = viewModel::updateGateway,
                        label = { Text("网关 IP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "状态：",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            when (authState) {
                                AuthState.LOGGED_IN -> "已连接"
                                AuthState.LOGGING_IN -> "连接中..."
                                AuthState.ERROR -> "错误"
                                AuthState.LOGGED_OUT -> "未连接"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (authState) {
                                AuthState.LOGGED_IN -> MaterialTheme.colorScheme.primary
                                AuthState.ERROR -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (authState == AuthState.LOGGED_IN) {
                        OutlinedButton(
                            onClick = { viewModel.logout() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("退出登录")
                        }
                    } else {
                        Button(
                            onClick = onNavigateToLogin,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("登录")
                        }
                    }
                }
            }

            // Polling section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "轮询",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "刷新间隔：${pollInterval} 秒",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = pollInterval.toFloat(),
                        onValueChange = { viewModel.updatePollInterval(it.toInt()) },
                        valueRange = 1f..30f,
                        steps = 28,
                    )
                }
            }

            // Appearance section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "外观",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("深色模式", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = darkMode ?: false,
                            onCheckedChange = viewModel::toggleDarkMode,
                        )
                    }
                }
            }

            // About section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "关于",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        versionLabel,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "ZTE U60 Pro (MU5120) 配套应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "本应用与中兴通讯股份有限公司无关联，也未获得其认可或赞助。ZTE 和 U60 Pro 是中兴通讯股份有限公司的商标。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
