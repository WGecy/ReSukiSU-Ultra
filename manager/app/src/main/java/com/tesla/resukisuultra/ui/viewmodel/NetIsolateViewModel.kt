package com.tesla.resukisuultra.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tesla.resukisuultra.data.netisolate.NetIsolateRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

data class NetIsolateUiState(
    val enabled: Boolean = false,
    val selectedUids: Set<Int> = emptySet(),
    val loaded: Boolean = false,
)

class NetIsolateViewModel(
    private val repository: NetIsolateRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NetIsolateUiState())
    val uiState: StateFlow<NetIsolateUiState> = mutableState.asStateFlow()

    /** 仿 SUSFS: 进入页面后台加载 (占位→数据更新, 不转圈; 缓存命中秒开) */
    fun refresh() {
        viewModelScope.launch {
            // 3s 超时兜底: 防止 shell/su 会话卡死导致永久等待 (阻塞感)
            withTimeoutOrNull(3000) {
                runCatching {
                    val enabledDeferred = async { repository.isEnabled() }
                    val uidsDeferred = async { repository.getUids() }
                    val enabled = enabledDeferred.await()
                    val uids = uidsDeferred.await()
                    NetIsolateUiState(enabled = enabled, selectedUids = uids, loaded = true)
                }.onSuccess { newState ->
                    mutableState.value = newState
                }
            } ?: run {
                // 超时: 标记已加载 (显示当前状态, 避免永久占位)
                mutableState.update { it.copy(loaded = true) }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        mutableState.update { it.copy(enabled = enabled) }
        viewModelScope.launch {
            repository.setEnabled(enabled)
        }
    }

    fun toggleUid(uid: Int) {
        val newSet = if (uid in mutableState.value.selectedUids) {
            mutableState.value.selectedUids - uid
        } else {
            mutableState.value.selectedUids + uid
        }
        mutableState.update { it.copy(selectedUids = newSet) }
        viewModelScope.launch {
            repository.setUids(newSet)
        }
    }
}
