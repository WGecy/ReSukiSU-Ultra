package com.tesla.resukisuultra.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tesla.resukisuultra.data.nomount.NoMountRepository
import com.tesla.resukisuultra.data.nomount.NoMountRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoMountUiState(
    val version: String = "",
    val supported: Boolean = false,
    val rules: List<NoMountRule> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

sealed interface NoMountUiAction {
    data object Refresh : NoMountUiAction
    data class AddRule(val virtual: String, val real: String) : NoMountUiAction
    data class RemoveRule(val virtual: String) : NoMountUiAction
    data object ClearRules : NoMountUiAction
}

class NoMountViewModel(
    private val repository: NoMountRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NoMountUiState())
    val uiState: StateFlow<NoMountUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun dispatch(action: NoMountUiAction) {
        when (action) {
            NoMountUiAction.Refresh -> refresh()
            is NoMountUiAction.AddRule -> {
                viewModelScope.launch {
                    if (repository.addRule(action.virtual, action.real)) {
                        refresh()
                    }
                }
            }

            is NoMountUiAction.RemoveRule -> {
                viewModelScope.launch {
                    if (repository.removeRule(action.virtual)) {
                        refresh()
                    }
                }
            }

            NoMountUiAction.ClearRules -> {
                viewModelScope.launch {
                    if (repository.clearRules()) {
                        refresh()
                    }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val status = repository.getStatus()
                val rules = repository.listRules()
                NoMountUiState(
                    version = status?.version.orEmpty(),
                    supported = status != null,
                    rules = rules,
                    isLoading = false,
                )
            }.onSuccess { mutableState.value = it }
                .onFailure { e ->
                    mutableState.update {
                        it.copy(isLoading = false, error = e.message)
                    }
                }
        }
    }
}
