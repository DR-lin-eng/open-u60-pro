package com.openu60.feature.router.qos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openu60.core.model.QoSConfig
import com.openu60.core.model.QoSParser
import com.openu60.core.network.AgentClient
import com.openu60.core.network.AgentError
import com.openu60.core.network.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QoSState(
    val config: QoSConfig = QoSConfig.empty,
    val isLoading: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

@HiltViewModel
class QoSViewModel @Inject constructor(
    private val agentClient: AgentClient,
    private val authManager: AuthManager,
) : ViewModel() {

    private val _state = MutableStateFlow(QoSState())
    val state: StateFlow<QoSState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                val data = agentClient.getJSON("/api/router/qos")
                val config = QoSParser.parse(data)
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
                agentClient.putJSON("/api/router/qos", mapOf("qos_switch" to if (enabled) "1" else "0"))
                _state.value = _state.value.copy(message = if (enabled) "QoS 已启用" else "QoS 已禁用", messageIsError = false)
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
            "当前设备暂不支持 QoS 接口"
        } else {
            msg
        }
    }

    private fun setError(msg: String?) {
        _state.value = _state.value.copy(isLoading = false, message = msg ?: "未知错误", messageIsError = true)
    }
}
