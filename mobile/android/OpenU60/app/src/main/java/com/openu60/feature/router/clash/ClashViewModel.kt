package com.openu60.feature.router.clash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openu60.core.model.ClashConnection
import com.openu60.core.model.ClashLogEntry
import com.openu60.core.model.ClashParser
import com.openu60.core.model.ClashProxyProvider
import com.openu60.core.model.ClashRule
import com.openu60.core.model.ClashRuleProvider
import com.openu60.core.model.ClashSelectorGroup
import com.openu60.core.model.ClashStatus
import com.openu60.core.network.AgentClient
import com.openu60.core.network.ClashDirectClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import javax.inject.Inject

data class ClashState(
    val status: ClashStatus = ClashStatus.empty,
    val selectors: List<ClashSelectorGroup> = emptyList(),
    val proxyProviders: List<ClashProxyProvider> = emptyList(),
    val ruleProviders: List<ClashRuleProvider> = emptyList(),
    val rules: List<ClashRule> = emptyList(),
    val connections: List<ClashConnection> = emptyList(),
    val logs: List<ClashLogEntry> = emptyList(),
    val latestDelayByName: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val isLogStreaming: Boolean = false,
    val logLevel: String = "info",
    val message: String? = null,
    val messageIsError: Boolean = false,
)

private data class ClashSnapshot(
    val status: ClashStatus,
    val selectors: List<ClashSelectorGroup>,
    val proxyProviders: List<ClashProxyProvider>,
    val ruleProviders: List<ClashRuleProvider>,
    val rules: List<ClashRule>,
    val connections: List<ClashConnection>,
)

@HiltViewModel
class ClashViewModel @Inject constructor(
    private val agentClient: AgentClient,
    private val clashClient: ClashDirectClient,
) : ViewModel() {

    private val _state = MutableStateFlow(ClashState())
    val state: StateFlow<ClashState> = _state.asStateFlow()

    private var logJob: Job? = null

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                applySnapshot(loadSnapshot())
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    status = ClashParser.offlineStatus(),
                    selectors = emptyList(),
                    proxyProviders = emptyList(),
                    ruleProviders = emptyList(),
                    rules = emptyList(),
                    connections = emptyList(),
                )
                setError(e.message)
            }
        }
    }

    fun setMode(mode: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                clashClient.patchJSON(agentClient.baseURL, "/configs", mapOf("mode" to mode))
                val snapshot = loadSnapshot()
                applySnapshot(snapshot, message = "模式已切换为 ${snapshot.status.modeLabel}")
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun select(group: String, name: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                clashClient.putJSON(
                    agentClient.baseURL,
                    "/proxies/${encodePath(group)}",
                    mapOf("name" to name),
                )
                applySnapshot(loadSnapshot(), message = "已切换 $group 到 $name")
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun testDelay(name: String) {
        viewModelScope.launch {
            try {
                val result = clashClient.getJSON(
                    agentClient.baseURL,
                    "/proxies/${encodePath(name)}/delay?timeout=5000&url=http://www.gstatic.com/generate_204",
                )
                val delay = ClashParser.parseDelay(result)
                if (delay != null) {
                    _state.value = _state.value.copy(
                        latestDelayByName = _state.value.latestDelayByName + (name to delay),
                        message = "$name 延迟 ${delay}ms",
                        messageIsError = false,
                    )
                }
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun closeConnection(id: String) {
        viewModelScope.launch {
            try {
                clashClient.deleteJSON(agentClient.baseURL, "/connections/${encodePath(id)}")
                val connections = ClashParser.parseConnections(
                    clashClient.getJSON(agentClient.baseURL, "/connections"),
                )
                _state.value = _state.value.copy(
                    connections = connections,
                    message = "连接已关闭",
                    messageIsError = false,
                )
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun setLogLevel(level: String) {
        val wasStreaming = _state.value.isLogStreaming
        _state.value = _state.value.copy(logLevel = level)
        if (wasStreaming) {
            stopLogs()
            startLogs(reset = false)
        }
    }

    fun clearLogs() {
        _state.value = _state.value.copy(logs = emptyList())
    }

    fun startLogs(reset: Boolean = true) {
        if (logJob?.isActive == true) return
        logJob = viewModelScope.launch {
            if (reset) {
                _state.value = _state.value.copy(logs = emptyList(), isLogStreaming = true, message = null)
            } else {
                _state.value = _state.value.copy(isLogStreaming = true, message = null)
            }
            try {
                clashClient.streamLogs(agentClient.baseURL, _state.value.logLevel) { entry ->
                    val parsed = ClashParser.parseLogEntry(entry, System.nanoTime())
                    val nextLogs = (_state.value.logs + parsed).takeLast(200)
                    _state.value = _state.value.copy(logs = nextLogs)
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                setError(e.message)
            } finally {
                _state.value = _state.value.copy(isLogStreaming = false)
            }
        }
    }

    fun stopLogs() {
        logJob?.cancel()
        logJob = null
        _state.value = _state.value.copy(isLogStreaming = false)
    }

    override fun onCleared() {
        super.onCleared()
        stopLogs()
    }

    private suspend fun loadSnapshot(): ClashSnapshot = coroutineScope {
        val versionJob = async { clashClient.getJSON(agentClient.baseURL, "/version") }
        val configsJob = async { clashClient.getJSON(agentClient.baseURL, "/configs") }
        val connectionsJob = async { clashClient.getJSON(agentClient.baseURL, "/connections") }
        val proxiesJob = async { clashClient.getJSON(agentClient.baseURL, "/proxies") }
        val rulesJob = async { clashClient.getJSON(agentClient.baseURL, "/rules") }
        val proxyProvidersJob = async { clashClient.getJSON(agentClient.baseURL, "/providers/proxies") }
        val ruleProvidersJob = async { clashClient.getJSON(agentClient.baseURL, "/providers/rules") }

        val version = versionJob.await()
        val configs = configsJob.await()
        val connections = connectionsJob.await()
        val proxies = proxiesJob.await()
        ClashSnapshot(
            status = ClashParser.parseStatus(version, configs, connections),
            selectors = ClashParser.parseSelectors(proxies),
            proxyProviders = ClashParser.parseProxyProviders(proxyProvidersJob.await()),
            ruleProviders = ClashParser.parseRuleProviders(ruleProvidersJob.await()),
            rules = ClashParser.parseRules(rulesJob.await()),
            connections = ClashParser.parseConnections(connections),
        )
    }

    private fun applySnapshot(snapshot: ClashSnapshot, message: String? = null) {
        _state.value = _state.value.copy(
            status = snapshot.status,
            selectors = snapshot.selectors,
            proxyProviders = snapshot.proxyProviders,
            ruleProviders = snapshot.ruleProviders,
            rules = snapshot.rules,
            connections = snapshot.connections,
            isLoading = false,
            message = message,
            messageIsError = false,
        )
    }

    private fun setError(msg: String?) {
        _state.value = _state.value.copy(
            isLoading = false,
            isLogStreaming = false,
            message = msg ?: "未知错误",
            messageIsError = true,
        )
    }

    private fun encodePath(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
