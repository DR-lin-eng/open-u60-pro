package com.openu60.feature.router.telemetry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openu60.core.model.DomainFilterConfig
import com.openu60.core.model.TelemetryParser
import com.openu60.core.network.AgentClient
import com.openu60.core.network.AgentError
import com.openu60.core.network.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TelemetryBlockerState(
    val config: DomainFilterConfig = DomainFilterConfig.empty,
    val isLoading: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val newDomain: String = "",
)

@HiltViewModel
class TelemetryBlockerViewModel @Inject constructor(
    private val agentClient: AgentClient,
    private val authManager: AuthManager,
) : ViewModel() {

    private val _state = MutableStateFlow(TelemetryBlockerState())
    val state: StateFlow<TelemetryBlockerState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                val data = agentClient.getJSON("/api/router/domain-filter")
                val config = TelemetryParser.parseDomainFilter(data)
                _state.value = _state.value.copy(config = config, isLoading = false)
            } catch (e: AgentError.Unauthorized) {
                if (authManager.reauthenticate()) refresh() else setError(e.message)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun toggleFilter(enabled: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                agentClient.putJSON("/api/router/domain-filter", mapOf("enable" to if (enabled) "1" else "0"))
                _state.value = _state.value.copy(message = if (enabled) "域名过滤已启用" else "域名过滤已禁用", messageIsError = false)
                refresh()
            } catch (e: AgentError.Unauthorized) {
                if (authManager.reauthenticate()) toggleFilter(enabled) else setError(e.message)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun updateNewDomain(value: String) {
        _state.value = _state.value.copy(newDomain = value)
    }

    fun addRule(domain: String) {
        val trimmed = domain.trim()
        if (trimmed.isEmpty()) {
            setError("请输入域名")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                agentClient.putJSON(
                    "/api/router/domain-filter",
                    mapOf(
                        "action" to "add",
                        "domain" to trimmed,
                        "enabled" to "1",
                    ),
                )
                val config = TelemetryParser.parseDomainFilter(agentClient.getJSON("/api/router/domain-filter"))
                _state.value = _state.value.copy(
                    config = config,
                    isLoading = false,
                    message = "已添加 $trimmed",
                    messageIsError = false,
                    newDomain = "",
                )
            } catch (e: AgentError.Unauthorized) {
                if (authManager.reauthenticate()) addRule(trimmed) else setError(e.message)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun removeRule(id: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                agentClient.putJSON(
                    "/api/router/domain-filter",
                    mapOf(
                        "action" to "delete",
                        "id" to id,
                    ),
                )
                val config = TelemetryParser.parseDomainFilter(agentClient.getJSON("/api/router/domain-filter"))
                _state.value = _state.value.copy(
                    config = config,
                    isLoading = false,
                    message = "规则已移除",
                    messageIsError = false,
                )
            } catch (e: AgentError.Unauthorized) {
                if (authManager.reauthenticate()) removeRule(id) else setError(e.message)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun blockAllTelemetry() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                val existing = _state.value.config.rules.map { it.domain.lowercase() }.toSet()
                var added = 0
                for (domain in TelemetryParser.knownTelemetryDomains) {
                    if (domain.lowercase() in existing) continue
                    agentClient.putJSON(
                        "/api/router/domain-filter",
                        mapOf(
                            "action" to "add",
                            "domain" to domain,
                            "enabled" to "1",
                        ),
                    )
                    added += 1
                }
                val config = TelemetryParser.parseDomainFilter(agentClient.getJSON("/api/router/domain-filter"))
                _state.value = _state.value.copy(
                    config = config,
                    isLoading = false,
                    message = if (added > 0) "已屏蔽 $added 个遥测域名" else "所有遥测域名均已存在",
                    messageIsError = false,
                )
            } catch (e: AgentError.Unauthorized) {
                if (authManager.reauthenticate()) blockAllTelemetry() else setError(e.message)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    private fun setError(msg: String?) {
        _state.value = _state.value.copy(isLoading = false, message = msg ?: "未知错误", messageIsError = true)
    }
}
