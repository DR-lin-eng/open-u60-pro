package com.openu60.feature.router.celllock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openu60.core.model.CellLockParser
import com.openu60.core.model.CellLockStatus
import com.openu60.core.model.NeighborCell
import com.openu60.core.network.AgentClient
import com.openu60.core.network.AgentError
import com.openu60.core.network.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CellLockState(
    val status: CellLockStatus = CellLockStatus.empty,
    val neighbors: List<NeighborCell> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val nrPCI: String = "",
    val nrEARFCN: String = "",
    val nrBand: String = "",
    val ltePCI: String = "",
    val lteEARFCN: String = "",
)

@HiltViewModel
class CellLockViewModel @Inject constructor(
    private val agentClient: AgentClient,
    private val authManager: AuthManager,
) : ViewModel() {

    private val _state = MutableStateFlow(CellLockState())
    val state: StateFlow<CellLockState> = _state.asStateFlow()

    fun updateField(field: String, value: String) {
        _state.value = when (field) {
            "nrPCI" -> _state.value.copy(nrPCI = value)
            "nrEARFCN" -> _state.value.copy(nrEARFCN = value)
            "nrBand" -> _state.value.copy(nrBand = value)
            "ltePCI" -> _state.value.copy(ltePCI = value)
            "lteEARFCN" -> _state.value.copy(lteEARFCN = value)
            else -> _state.value
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                val data = agentClient.getJSON("/api/network/signal")
                val status = CellLockParser.parse(data)
                _state.value = _state.value.copy(status = status, isLoading = false)
            } catch (e: AgentError.Unauthorized) {
                if (authManager.reauthenticate()) refresh() else setError(e.message)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun lockCell() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                val s = _state.value
                var applied = false
                if (s.nrPCI.isNotBlank() && s.nrEARFCN.isNotBlank()) {
                    val nrParams = mutableMapOf<String, Any>("pci" to s.nrPCI, "earfcn" to s.nrEARFCN)
                    if (s.nrBand.isNotBlank()) nrParams["band"] = s.nrBand
                    agentClient.postJSON("/api/cell/lock/nr", nrParams)
                    applied = true
                }
                if (s.ltePCI.isNotBlank() && s.lteEARFCN.isNotBlank()) {
                    agentClient.postJSON("/api/cell/lock/lte", mapOf("pci" to s.ltePCI, "earfcn" to s.lteEARFCN))
                    applied = true
                }
                if (!applied) throw IllegalArgumentException("请至少填写一组可锁定的小区参数")
                _state.value = _state.value.copy(
                    isLoading = false,
                    message = "小区锁定已应用",
                    messageIsError = false,
                )
                refresh()
            } catch (e: AgentError.Unauthorized) {
                if (authManager.reauthenticate()) lockCell() else setError(e.message)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun unlockCell() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                agentClient.postJSON("/api/cell/lock/reset")
                _state.value = _state.value.copy(
                    status = CellLockStatus.empty,
                    isLoading = false,
                    message = "小区锁定已解除",
                    messageIsError = false,
                    nrPCI = "", nrEARFCN = "", nrBand = "",
                    ltePCI = "", lteEARFCN = "",
                )
            } catch (e: AgentError.Unauthorized) {
                if (authManager.reauthenticate()) unlockCell() else setError(e.message)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun scanNeighbors() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                agentClient.postJSON("/api/cell/neighbors/scan")
                delay(3000)
                val nrData = runCatching { agentClient.getJSON("/api/cell/neighbors/nr") }.getOrDefault(emptyMap())
                val lteData = runCatching { agentClient.getJSON("/api/cell/neighbors/lte") }.getOrDefault(emptyMap())
                val neighbors = buildList {
                    addAll(CellLockParser.parseNeighbors(nrData, "NR"))
                    addAll(CellLockParser.parseNeighbors(lteData, "LTE"))
                }
                _state.value = _state.value.copy(
                    neighbors = neighbors,
                    isLoading = false,
                    message = "找到 ${neighbors.size} 个邻区",
                    messageIsError = false,
                )
            } catch (e: AgentError.Unauthorized) {
                if (authManager.reauthenticate()) scanNeighbors() else setError(e.message)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    private fun setError(msg: String?) {
        _state.value = _state.value.copy(isLoading = false, message = msg ?: "未知错误", messageIsError = true)
    }
}
