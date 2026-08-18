package com.tesla.resukisuultra.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tesla.resukisuultra.data.nomount.NoMountModule
import com.tesla.resukisuultra.data.nomount.NoMountRepository
import com.tesla.resukisuultra.data.nomount.NoMountRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoMountModuleRules(
    val moduleName: String,
    val rules: List<NoMountRule>,
)

data class NoMountUiState(
    val version: String = "",
    val supported: Boolean = false,
    val rules: List<NoMountRule> = emptyList(),
    val moduleRules: List<NoMountModuleRules> = emptyList(),
    val customRules: List<NoMountRule> = emptyList(),
    val modules: List<NoMountModule> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

sealed interface NoMountUiAction {
    data object Refresh : NoMountUiAction
    data class AddRule(val virtual: String, val real: String) : NoMountUiAction
    data class RemoveRule(val virtual: String) : NoMountUiAction
    data object ClearRules : NoMountUiAction
    data class LoadModule(val moduleId: String) : NoMountUiAction
    data class UnloadModule(val moduleId: String) : NoMountUiAction
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

            is NoMountUiAction.LoadModule -> {
                viewModelScope.launch {
                    if (repository.loadModule(action.moduleId)) {
                        refresh()
                    }
                }
            }

            is NoMountUiAction.UnloadModule -> {
                viewModelScope.launch {
                    if (repository.unloadModule(action.moduleId)) {
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
                val modules = repository.listModules()
                val modulePrefix = "/data/adb/modules/"
                // 模块注入规则: real 路径以 /data/adb/modules/<模块名>/ 开头 → 按模块分组
                val moduleMap = LinkedHashMap<String, MutableList<NoMountRule>>()
                val custom = mutableListOf<NoMountRule>()
                for (rule in rules) {
                    if (rule.real.startsWith(modulePrefix)) {
                        val parts = rule.real.removePrefix(modulePrefix).split('/')
                        val module = parts.firstOrNull().orEmpty().ifBlank { "unknown" }
                        moduleMap.getOrPut(module) { mutableListOf() }.add(rule)
                    } else {
                        custom.add(rule)
                    }
                }
                NoMountUiState(
                    version = status?.version.orEmpty(),
                    supported = status != null,
                    rules = rules,
                    moduleRules = moduleMap.map { (name, rs) ->
                        NoMountModuleRules(name, rs)
                    },
                    customRules = custom,
                    modules = modules,
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
