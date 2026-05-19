package com.openu60.feature.router.apn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openu60.core.model.APNConfig
import com.openu60.core.model.APNParser
import com.openu60.core.model.APNProfile
import com.openu60.core.network.AgentClient
import com.openu60.core.network.AgentError
import com.openu60.core.network.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class APNState(
    val config: APNConfig = APNConfig.empty,
    val isLoading: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val showForm: Boolean = false,
    val editingProfile: APNProfile? = null,
)

@HiltViewModel
class APNViewModel @Inject constructor(
    private val agentClient: AgentClient,
    private val authManager: AuthManager,
) : ViewModel() {

    private val _state = MutableStateFlow(APNState())
    val state: StateFlow<APNState> = _state.asStateFlow()

    fun showAddForm() {
        _state.value = _state.value.copy(showForm = true, editingProfile = APNProfile.empty)
    }

    fun showEditForm(profile: APNProfile) {
        _state.value = _state.value.copy(showForm = true, editingProfile = profile)
    }

    fun hideForm() {
        _state.value = _state.value.copy(showForm = false, editingProfile = null)
    }

    fun updateEditingProfile(profile: APNProfile) {
        _state.value = _state.value.copy(editingProfile = profile)
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                val modeData = agentClient.getJSON("/api/router/apn/mode")
                val profilesData = agentClient.getJSON("/api/router/apn/profiles")
                val autoProfilesData = agentClient.getJSON("/api/router/apn/auto-profiles")
                val mode = APNParser.parseMode(modeData)
                val profiles = APNParser.parseProfiles(profilesData)
                val autoProfiles = APNParser.parseProfiles(autoProfilesData)
                _state.value = _state.value.copy(
                    config = APNConfig(mode = mode, profiles = profiles, autoProfiles = autoProfiles),
                    isLoading = false,
                )
            } catch (e: AgentError.Unauthorized) {
                if (authManager.reauthenticate()) refresh() else setError(e.message)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun setMode(mode: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                agentClient.putJSON("/api/router/apn/mode", mapOf("apn_mode" to (mode.toIntOrNull() ?: 0)))
                _state.value = _state.value.copy(
                    config = _state.value.config.copy(mode = mode),
                    isLoading = false,
                    message = if (mode == "1") "已切换为手动 APN 模式" else "已切换为自动 APN 模式",
                    messageIsError = false,
                )
            } catch (e: AgentError.Unauthorized) {
                if (authManager.reauthenticate()) setMode(mode) else setError(e.message)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun saveProfile() {
        val profile = _state.value.editingProfile ?: return
        val existingNames = _state.value.config.profiles
            .filter { it.id != profile.id }
            .map { it.name.lowercase() }
        if (profile.name.lowercase() in existingNames) {
            _state.value = _state.value.copy(message = "配置名称已存在", messageIsError = true)
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                val params = mapOf(
                    "profileId" to profile.id,
                    "profilename" to profile.name,
                    "wanapn" to profile.apn,
                    "pdpType" to profile.pdpType,
                    "pppAuthMode" to profile.authMode,
                    "username" to profile.username,
                    "password" to profile.password,
                )
                if (profile.id.isNotBlank()) {
                    agentClient.putJSON("/api/router/apn/profiles", params)
                } else {
                    agentClient.postJSON("/api/router/apn/profiles", params)
                }
                _state.value = _state.value.copy(showForm = false, editingProfile = null, isLoading = false, message = "配置已保存", messageIsError = false)
                refresh()
            } catch (e: AgentError.Unauthorized) {
                if (authManager.reauthenticate()) saveProfile() else setError(e.message)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                agentClient.postJSON("/api/router/apn/profiles/delete", mapOf("profileId" to id))
                _state.value = _state.value.copy(isLoading = false, message = "配置已删除", messageIsError = false)
                refresh()
            } catch (e: AgentError.Unauthorized) {
                if (authManager.reauthenticate()) deleteProfile(id) else setError(e.message)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun activateProfile(id: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                agentClient.postJSON("/api/router/apn/profiles/activate", mapOf("profileId" to id))
                _state.value = _state.value.copy(isLoading = false, message = "配置已启用", messageIsError = false)
                refresh()
            } catch (e: AgentError.Unauthorized) {
                if (authManager.reauthenticate()) activateProfile(id) else setError(e.message)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    private fun setError(msg: String?) {
        _state.value = _state.value.copy(isLoading = false, message = msg ?: "未知错误", messageIsError = true)
    }
}
