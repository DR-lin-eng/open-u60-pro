package com.openu60.feature.router.clash

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openu60.core.model.ClashConnection
import com.openu60.core.model.ClashProxyNode
import com.openu60.core.model.ClashProxyProvider
import com.openu60.core.model.ClashRule
import com.openu60.core.model.ClashRuleProvider
import com.openu60.core.model.ClashSelectorGroup
import com.openu60.core.model.ClashStatus
import com.openu60.core.model.DeviceParser

private enum class ClashTab(val title: String) {
    Overview("概览"),
    Proxies("代理"),
    Connections("连接"),
    Rules("规则"),
    Logs("日志"),
    Settings("设置"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashScreen(
    onBack: () -> Unit,
    viewModel: ClashViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var proxyQuery by rememberSaveable { mutableStateOf("") }
    var connectionQuery by rememberSaveable { mutableStateOf("") }
    var ruleQuery by rememberSaveable { mutableStateOf("") }
    val tabs = ClashTab.entries

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(selectedTabIndex, state.logLevel) {
        if (tabs[selectedTabIndex] == ClashTab.Logs) viewModel.startLogs()
        else viewModel.stopLogs()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopLogs() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clash 面板") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            state.message?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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

            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(tab.title) },
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when (tabs[selectedTabIndex]) {
                    ClashTab.Overview -> ClashOverviewTab(state = state, onSetMode = viewModel::setMode)
                    ClashTab.Proxies -> ClashProxiesTab(
                        state = state,
                        query = proxyQuery,
                        onQueryChange = { proxyQuery = it },
                        onSelect = viewModel::select,
                        onTestDelay = viewModel::testDelay,
                    )
                    ClashTab.Connections -> ClashConnectionsTab(
                        state = state,
                        query = connectionQuery,
                        onQueryChange = { connectionQuery = it },
                        onCloseConnection = viewModel::closeConnection,
                    )
                    ClashTab.Rules -> ClashRulesTab(
                        state = state,
                        query = ruleQuery,
                        onQueryChange = { ruleQuery = it },
                    )
                    ClashTab.Logs -> ClashLogsTab(
                        state = state,
                        onLevelChange = viewModel::setLogLevel,
                        onStart = { viewModel.startLogs(reset = false) },
                        onStop = viewModel::stopLogs,
                        onClear = viewModel::clearLogs,
                    )
                    ClashTab.Settings -> ClashSettingsTab(state = state.status)
                }
            }
        }
    }
}

@Composable
private fun ClashOverviewTab(
    state: ClashState,
    onSetMode: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OverviewMetricCard(
            title = "运行状态",
            primary = if (state.status.running) "在线" else "离线",
            secondary = "Clash Meta ${state.status.version.ifBlank { "--" }}",
            accent = if (state.status.running) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
        )
        OverviewMetricCard(
            title = "连接与流量",
            primary = "${state.status.connections} 条连接",
            secondary = "下行 ${DeviceParser.formatBytes(state.status.downloadTotal)} / 上行 ${DeviceParser.formatBytes(state.status.uploadTotal)}",
        )
        OverviewMetricCard(
            title = "监听端口",
            primary = "Controller ${state.status.controllerPort}",
            secondary = "Mixed ${state.status.mixedPort} / Redir ${state.status.redirPort} / TProxy ${state.status.tproxyPort}",
        )

        if (state.status.running) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeChip("规则", "rule", state.status.mode, onSetMode)
                        ModeChip("全局", "global", state.status.mode, onSetMode)
                        ModeChip("直连", "direct", state.status.mode, onSetMode)
                    }
                }
            }
        } else {
            OfflineHint()
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("运行摘要", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SummaryLine("面板", state.status.ui.ifBlank { "zashboard" })
                SummaryLine("当前模式", state.status.modeLabel)
                SummaryLine("日志级别", state.status.logLevel.ifBlank { "--" })
                SummaryLine("指纹", state.status.globalClientFingerprint.ifBlank { "--" })
            }
        }
    }
}

@Composable
private fun ClashProxiesTab(
    state: ClashState,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (String, String) -> Unit,
    onTestDelay: (String) -> Unit,
) {
    val expandedProviders = remember { mutableStateMapOf<String, Boolean>() }
    val normalizedQuery = query.trim().lowercase()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("筛选策略组 / 节点 / Provider") },
                singleLine = true,
            )

            if (!state.status.running) {
                OfflineHint()
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) {
            val selectors = state.selectors.filter { selector ->
                normalizedQuery.isEmpty() ||
                    selector.name.lowercase().contains(normalizedQuery) ||
                    selector.current.lowercase().contains(normalizedQuery) ||
                    selector.options.any { it.lowercase().contains(normalizedQuery) }
            }
            if (selectors.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "策略组",
                        subtitle = "${selectors.size} 组，保持 Clash API 原始顺序",
                    )
                }
                items(selectors, key = { it.name }) { selector ->
                    SelectorCard(
                        selector = selector,
                        delayMs = state.latestDelayByName[selector.name] ?: selector.latestDelayMs,
                        onSelect = onSelect,
                        onTestDelay = onTestDelay,
                    )
                }
            }

            val providers = state.proxyProviders.filter { provider ->
                normalizedQuery.isEmpty() ||
                    provider.name.lowercase().contains(normalizedQuery) ||
                    provider.nodes.any { node ->
                        node.name.lowercase().contains(normalizedQuery) ||
                            node.current.lowercase().contains(normalizedQuery)
                    }
            }
            if (providers.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "Proxy Providers",
                        subtitle = "${providers.size} 个订阅 / 分组",
                    )
                }
                items(providers, key = { it.name }) { provider ->
                    val expanded = expandedProviders[provider.name] ?: false
                    ProviderCard(
                        provider = provider,
                        expanded = expanded,
                        delayMap = state.latestDelayByName,
                        onToggle = { expandedProviders[provider.name] = !expanded },
                        onTestDelay = onTestDelay,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClashConnectionsTab(
    state: ClashState,
    query: String,
    onQueryChange: (String) -> Unit,
    onCloseConnection: (String) -> Unit,
) {
    val normalizedQuery = query.trim().lowercase()
    val filteredConnections = state.connections.filter { connection ->
        normalizedQuery.isEmpty() ||
            connection.host.lowercase().contains(normalizedQuery) ||
            connection.destination.lowercase().contains(normalizedQuery) ||
            connection.rule.lowercase().contains(normalizedQuery) ||
            connection.chains.any { it.lowercase().contains(normalizedQuery) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("筛选域名 / 规则 / 节点") },
                singleLine = true,
            )
            Text(
                "当前 ${filteredConnections.size} / ${state.connections.size} 条连接",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) {
            items(filteredConnections, key = { it.id }) { connection ->
                ConnectionCard(connection = connection, onCloseConnection = onCloseConnection)
            }
        }
    }
}

@Composable
private fun ClashRulesTab(
    state: ClashState,
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val normalizedQuery = query.trim().lowercase()
    val filteredRules = state.rules.filter { rule ->
        normalizedQuery.isEmpty() ||
            rule.type.lowercase().contains(normalizedQuery) ||
            rule.payload.lowercase().contains(normalizedQuery) ||
            rule.proxy.lowercase().contains(normalizedQuery)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("筛选规则 / Payload / 出站") },
                singleLine = true,
            )
            if (state.ruleProviders.isNotEmpty()) {
                Text(
                    "Rule Providers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                state.ruleProviders.forEach { provider ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(provider.name, fontWeight = FontWeight.Medium)
                                Text(
                                    "${provider.behavior} / ${provider.format}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${provider.ruleCount} 条",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
            Text(
                "规则总数 ${filteredRules.size} / ${state.rules.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) {
            items(filteredRules, key = { "${it.type}:${it.payload}:${it.proxy}" }) { rule ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(rule.type.ifBlank { "--" }, fontWeight = FontWeight.SemiBold)
                            Text(
                                rule.proxy.ifBlank { "--" },
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        SelectionContainer {
                            Text(
                                rule.payload.ifBlank { "(empty payload)" },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClashLogsTab(
    state: ClashState,
    onLevelChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
) {
    val levels = listOf("info", "warning", "error", "debug", "silent")

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("日志级别", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                levels.forEach { level ->
                    FilterChip(
                        selected = state.logLevel == level,
                        onClick = { onLevelChange(level) },
                        label = { Text(level) },
                    )
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = onStart,
                    label = { Text(if (state.isLogStreaming) "采集中" else "开始采集") },
                    leadingIcon = {
                        if (state.isLogStreaming) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                        }
                    },
                )
                AssistChip(
                    onClick = onStop,
                    label = { Text("停止") },
                    leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null) },
                )
                AssistChip(
                    onClick = onClear,
                    label = { Text("清空") },
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) {
            items(state.logs.asReversed(), key = { it.id }) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LogTypeBadge(entry.type)
                            Text(
                                entry.id.toString().takeLast(6),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SelectionContainer {
                            Text(
                                entry.payload,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClashSettingsTab(state: ClashStatus) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("运行配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SummaryLine("Allow LAN", yesNo(state.allowLan))
                SummaryLine("IPv6", yesNo(state.ipv6))
                SummaryLine("TUN", yesNo(state.tunEnabled))
                SummaryLine("Unified Delay", yesNo(state.unifiedDelay))
                SummaryLine("Sniffing", yesNo(state.sniffing))
                SummaryLine("Geodata Mode", yesNo(state.geodataMode))
                SummaryLine("Geo Update", "${state.geoUpdateInterval}h")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("网络与绑定", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SummaryLine("Bind Address", state.bindAddress.ifBlank { "*" })
                SummaryLine("Interface", state.interfaceName.ifBlank { "--" })
                SummaryLine("Mixed Port", state.mixedPort.toString())
                SummaryLine("Redir Port", state.redirPort.toString())
                SummaryLine("TProxy Port", state.tproxyPort.toString())
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("客户端特征", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SelectionContainer {
                    Text(
                        state.globalClientFingerprint.ifBlank { "--" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewMetricCard(
    title: String,
    primary: String,
    secondary: String,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(primary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
            Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorCard(
    selector: ClashSelectorGroup,
    delayMs: Int?,
    onSelect: (String, String) -> Unit,
    onTestDelay: (String) -> Unit,
) {
    var expanded by remember(selector.name, selector.current) { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(selector.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Text(
                        "${selector.options.size} 个候选",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DelayChip(delayMs = delayMs, alive = selector.alive)
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = selector.current.ifBlank { "未选择" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(if (selector.alive) "当前节点" else "当前节点（组离线）") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    singleLine = true,
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    selector.options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                onSelect(selector.name, option)
                                expanded = false
                            },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onTestDelay(selector.name) }) {
                    Text("延迟测试")
                }
                Text(
                    selector.current.ifBlank { "--" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ClashProxyProvider,
    expanded: Boolean,
    delayMap: Map<String, Int>,
    onToggle: () -> Unit,
    onTestDelay: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(provider.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Text(
                        "${provider.vehicleType} / ${provider.type} / ${provider.aliveCount}/${provider.nodes.size} 在线",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onToggle) {
                    Text(if (expanded) "收起" else "展开")
                }
            }

            if (expanded) {
                provider.nodes.forEach { node ->
                    ProxyNodeRow(
                        node = node,
                        delayMs = delayMap[node.name] ?: node.latestDelayMs,
                        onTestDelay = onTestDelay,
                    )
                }
            } else {
                provider.nodes.take(3).forEach { node ->
                    ProxyNodeRow(
                        node = node,
                        delayMs = delayMap[node.name] ?: node.latestDelayMs,
                        onTestDelay = onTestDelay,
                    )
                }
                if (provider.nodes.size > 3) {
                    Text(
                        "其余 ${provider.nodes.size - 3} 个节点已折叠",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProxyNodeRow(
    node: ClashProxyNode,
    delayMs: Int?,
    onTestDelay: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(node.name.ifBlank { "--" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        node.type.ifBlank { "--" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DelayChip(delayMs = delayMs, alive = node.alive)
            }
            if (node.current.isNotBlank()) {
                Text(
                    "当前: ${node.current}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onTestDelay(node.name) }) {
                    Text("测试")
                }
                if (node.options.isNotEmpty()) {
                    Text(
                        "${node.options.size} 个候选",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    connection: ClashConnection,
    onCloseConnection: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        connection.host.ifBlank { connection.destination.ifBlank { "(no host)" } },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${connection.network}/${connection.type}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onCloseConnection(connection.id) }) {
                    Icon(Icons.Default.Close, contentDescription = "关闭连接")
                }
            }

            SummaryLine("出站", connection.chains.joinToString(" -> ").ifBlank { "--" })
            SummaryLine("规则", listOf(connection.rule, connection.rulePayload).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "--" })
            SummaryLine("源地址", connection.source.ifBlank { "--" })
            SummaryLine("目标地址", connection.destination.ifBlank { "--" })
            SummaryLine("上传 / 下载", "${DeviceParser.formatBytes(connection.upload)} / ${DeviceParser.formatBytes(connection.download)}")
            if (connection.process.isNotBlank()) {
                SummaryLine("进程", connection.process)
            }
            if (connection.startedAt.isNotBlank()) {
                SummaryLine("开始时间", connection.startedAt.replace("T", " ").replace("Z", ""))
            }
        }
    }
}

@Composable
private fun LogTypeBadge(type: String) {
    val lower = type.lowercase()
    val color = when (lower) {
        "error" -> MaterialTheme.colorScheme.error
        "warning" -> Color(0xFFEF6C00)
        "debug" -> Color(0xFF1565C0)
        else -> MaterialTheme.colorScheme.primary
    }
    Text(
        text = lower.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun DelayChip(delayMs: Int?, alive: Boolean) {
    val label = when {
        delayMs != null -> "${delayMs}ms"
        alive -> "在线"
        else -> "离线"
    }
    val color = when {
        delayMs == null && !alive -> MaterialTheme.colorScheme.error
        delayMs != null && delayMs <= 100 -> Color(0xFF2E7D32)
        delayMs != null && delayMs <= 250 -> Color(0xFFEF6C00)
        delayMs != null -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.widthIn(min = 88.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f)) {
            SelectionContainer {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, value: String, current: String, onSelect: (String) -> Unit) {
    FilterChip(
        selected = current.equals(value, ignoreCase = true),
        onClick = { onSelect(value) },
        label = { Text(label) },
    )
}

@Composable
private fun OfflineHint() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            "当前无法直连 Clash 控制器，请确认 7788 端口可访问，且 secret 仍为默认 123456。",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun yesNo(value: Boolean): String = if (value) "已开启" else "已关闭"
