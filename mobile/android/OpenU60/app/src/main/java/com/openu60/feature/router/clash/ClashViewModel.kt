package com.openu60.feature.router.clash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openu60.core.model.ClashParser
import com.openu60.core.model.ClashSelectorGroup
import com.openu60.core.model.ClashStatus
import com.openu60.core.network.AgentClient
import com.openu60.core.network.ClashDirectClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import javax.inject.Inject

data class ClashState(
    val status: ClashStatus = ClashStatus.empty,
    val selectors: List<ClashSelectorGroup> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

@HiltViewModel
class ClashViewModel @Inject constructor(
    private val agentClient: AgentClient,
    private val clashClient: ClashDirectClient,
) : ViewModel() {

    private val _state = MutableStateFlow(ClashState())
    val state: StateFlow<ClashState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                val version = clashClient.getJSON(agentClient.baseURL, "/version")
                val configs = clashClient.getJSON(agentClient.baseURL, "/configs")
                val connections = clashClient.getJSON(agentClient.baseURL, "/connections")
                val proxies = clashClient.getJSON(agentClient.baseURL, "/proxies")
                val status = ClashParser.parseStatus(version, configs, connections)
                val selectors = ClashParser.parseSelectors(proxies)
                _state.value = _state.value.copy(
                    status = status,
                    selectors = selectors,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(status = ClashParser.offlineStatus(), selectors = emptyList())
                setError(e.message)
            }
        }
    }

    fun setMode(mode: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                clashClient.patchJSON(agentClient.baseURL, "/configs", mapOf("mode" to mode))
                val status = ClashParser.parseStatus(
                    clashClient.getJSON(agentClient.baseURL, "/version"),
                    clashClient.getJSON(agentClient.baseURL, "/configs"),
                    clashClient.getJSON(agentClient.baseURL, "/connections"),
                )
                _state.value = _state.value.copy(
                    status = status,
                    isLoading = false,
                    message = "模式已切换为 ${status.modeLabel}",
                    messageIsError = false,
                )
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
                    "/proxies/${URLEncoder.encode(group, Charsets.UTF_8.name()).replace("+", "%20")}",
                    mapOf(
                        "name" to name,
                    ),
                )
                refreshWithMessage("已切换 $group 到 $name")
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    private suspend fun refreshWithMessage(message: String) {
        val version = clashClient.getJSON(agentClient.baseURL, "/version")
        val configs = clashClient.getJSON(agentClient.baseURL, "/configs")
        val connections = clashClient.getJSON(agentClient.baseURL, "/connections")
        val proxies = clashClient.getJSON(agentClient.baseURL, "/proxies")
        val status = ClashParser.parseStatus(version, configs, connections)
        val selectors = ClashParser.parseSelectors(proxies)
        _state.value = _state.value.copy(
            status = status,
            selectors = selectors,
            isLoading = false,
            message = message,
            messageIsError = false,
        )
    }

    private fun setError(msg: String?) {
        _state.value = _state.value.copy(isLoading = false, message = msg ?: "未知错误", messageIsError = true)
    }
}
