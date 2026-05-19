package com.openu60.feature.router.stc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openu60.core.model.STCConfig
import com.openu60.core.model.STCParser
import com.openu60.core.network.AgentClient
import com.openu60.core.network.AgentError
import com.openu60.core.network.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class STCState(
    val config: STCConfig = STCConfig.empty,
    val isLoading: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

@HiltViewModel
class STCViewModel @Inject constructor(
    private val agentClient: AgentClient,
    private val authManager: AuthManager,
) : ViewModel() {

    private val _state = MutableStateFlow(STCState())
    val state: StateFlow<STCState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                val paramsData = agentClient.getJSON("/api/cell/stc/params")
                var config = STCParser.parseParams(paramsData)

                try {
                    val statusData = agentClient.getJSON("/api/cell/stc/status")
                    config = STCParser.parseStatus(statusData, config)
                } catch (_: Exception) {}

                _state.value = _state.value.copy(config = config, isLoading = false)
            } catch (e: AgentError.Unauthorized) {
                if (authManager.reauthenticate()) refresh() else setError(e.message)
            } catch (e: Exception) {
                setError(normalizeMessage(e.message))
            }
        }
    }

    fun toggle(enabled: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                if (enabled) {
                    agentClient.postJSON("/api/cell/stc/enable")
                } else {
                    agentClient.postJSON("/api/cell/stc/disable")
                }
                _state.value = _state.value.copy(message = if (enabled) "STC 已启用" else "STC 已禁用", messageIsError = false)
                refresh()
            } catch (e: AgentError.Unauthorized) {
                if (authManager.reauthenticate()) toggle(enabled) else setError(e.message)
            } catch (e: Exception) {
                setError(normalizeMessage(e.message))
            }
        }
    }

    private fun normalizeMessage(msg: String?): String? {
        return if (msg?.contains("Method not found", ignoreCase = true) == true) {
            "当前设备暂不支持 STC 接口"
        } else {
            msg
        }
    }

    private fun setError(msg: String?) {
        _state.value = _state.value.copy(isLoading = false, message = msg ?: "未知错误", messageIsError = true)
    }
}
