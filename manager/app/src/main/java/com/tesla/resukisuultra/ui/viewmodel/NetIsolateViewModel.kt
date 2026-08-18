package com.tesla.resukisuultra.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tesla.resukisuultra.data.netisolate.NetIsolateRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    /** 仿 SUSFS: 进入页面后台加载 (suspend 可等待 — 页面级 pageLoaded 在真实完成后置位) */
    suspend fun refresh() {
        coroutineScope {
            val enabledDeferred = async { repository.isEnabled() }
            val uidsDeferred = async { repository.getUids() }
            val enabled = enabledDeferred.await()
            val uids = uidsDeferred.await()
            mutableState.value = NetIsolateUiState(enabled = enabled, selectedUids = uids, loaded = true)
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
