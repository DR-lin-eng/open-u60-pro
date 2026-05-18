package com.openu60.feature.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsListScreen(
    onNavigateToDeviceInfo: () -> Unit,
    onNavigateToClients: () -> Unit,
    onNavigateToBandLock: () -> Unit,
    onNavigateToEnableADB: () -> Unit,
    onNavigateToConfigTool: () -> Unit,
    onNavigateToScheduler: () -> Unit,
    onNavigateToUSBMode: () -> Unit,
    onNavigateToSpeedTest: () -> Unit,
    onNavigateToLANSpeedTest: () -> Unit,
    onNavigateToSMSForward: () -> Unit,
    onNavigateToProcessList: () -> Unit,
    onNavigateToATTerminal: () -> Unit,
    onNavigateToPlaceholder: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("工具") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "网络工具",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            ToolItem(
                icon = Icons.Default.Info,
                title = "设备信息",
                subtitle = "SIM、IMEI、WAN IP 信息",
                onClick = onNavigateToDeviceInfo,
            )
            ToolItem(
                icon = Icons.Default.Devices,
                title = "已连接设备",
                subtitle = "查看已连接客户端和 DHCP 租约",
                onClick = onNavigateToClients,
            )
            ToolItem(
                icon = Icons.Default.CellTower,
                title = "频段锁定",
                subtitle = "锁定 NR/LTE 频段",
                onClick = onNavigateToBandLock,
            )
            ToolItem(
                icon = Icons.Default.Adb,
                title = "启用 ADB",
                subtitle = "启用 USB 调试模式",
                onClick = onNavigateToEnableADB,
            )
            ToolItem(
                icon = Icons.Default.Schedule,
                title = "定时任务",
                subtitle = "定时执行自动化任务",
                onClick = onNavigateToScheduler,
            )
            ToolItem(
                icon = Icons.Default.ForwardToInbox,
                title = "短信转发",
                subtitle = "自动将短信转发到 Telegram、Webhook 等",
                onClick = onNavigateToSMSForward,
            )
            ToolItem(
                icon = Icons.Default.Usb,
                title = "USB 模式",
                subtitle = "USB 模式与充电宝控制",
                onClick = onNavigateToUSBMode,
            )
            ToolItem(
                icon = Icons.Default.Speed,
                title = "速度测试",
                subtitle = "测试广域网吞吐",
                onClick = onNavigateToSpeedTest,
            )
            ToolItem(
                icon = Icons.Default.Wifi,
                title = "局域网测速",
                subtitle = "测试到路由器的 Wi-Fi 链路",
                onClick = onNavigateToLANSpeedTest,
            )
            ToolItem(
                icon = Icons.Default.Memory,
                title = "进程监控",
                subtitle = "查看进程并清理臃肿守护进程",
                onClick = onNavigateToProcessList,
            )
            ToolItem(
                icon = Icons.Default.Terminal,
                title = "AT 终端",
                subtitle = "向调制解调器发送原始 AT 命令",
                onClick = onNavigateToATTerminal,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "配置工具",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            ToolItem(
                icon = Icons.Default.Security,
                title = "配置解密/加密",
                subtitle = "离线 ZXHN 配置文件工具",
                onClick = onNavigateToConfigTool,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "仅 ADB 工具",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            ToolItem(
                icon = Icons.Default.Timer,
                title = "TTL 修复",
                subtitle = "需要 ADB USB 连接",
                onClick = { onNavigateToPlaceholder("TTL 修复") },
                enabled = false,
            )
            ToolItem(
                icon = Icons.Default.Terminal,
                title = "SSH 访问",
                subtitle = "需要 ADB USB 连接",
                onClick = { onNavigateToPlaceholder("SSH 访问") },
                enabled = false,
            )
            ToolItem(
                icon = Icons.Default.FolderOpen,
                title = "设备浏览器",
                subtitle = "需要 ADB USB 连接",
                onClick = { onNavigateToPlaceholder("设备浏览器") },
                enabled = false,
            )
        }
    }
}

@Composable
private fun ToolItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        colors = if (enabled) {
            CardDefaults.cardColors()
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
        },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
            if (enabled) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
