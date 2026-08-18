package com.tesla.resukisuultra.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tesla.resukisuultra.data.nomount.NoMountModule
import com.tesla.resukisuultra.data.nomount.NoMountRepository
import com.tesla.resukisuultra.data.nomount.NoMountRule
import kotlinx.coroutines.async
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
    val exclusions: List<Long> = emptyList(),
    val isLoading: Boolean = false,
    val loadedTabs: Set<Int> = emptySet(),
    val error: String? = null,
)

sealed interface NoMountUiAction {
    data object Refresh : NoMountUiAction
    data class AddRule(val virtual: String, val real: String) : NoMountUiAction
    data class RemoveRule(val virtual: String) : NoMountUiAction
    data object ClearRules : NoMountUiAction
    data class LoadModule(val moduleId: String) : NoMountUiAction
    data class UnloadModule(val moduleId: String) : NoMountUiAction
    data class AddExclusion(val uid: Long) : NoMountUiAction
    data class RemoveExclusion(val uid: Long) : NoMountUiAction
}

class NoMountViewModel(
    private val repository: NoMountRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NoMountUiState())
    val uiState: StateFlow<NoMountUiState> = mutableState.asStateFlow()

    /** 进入页面并行加载全部数据 (一次刷新 3 个数据源并行, 切 tab 秒开)
     *  silent=true: 已有数据时静默刷新 (不转圈, 避免每次进入卡顿) */
    fun refreshAll(silent: Boolean = false) {
        val current = mutableState.value
        if (current.isLoading) return
        viewModelScope.launch {
            if (!silent) {
                mutableState.update { it.copy(isLoading = true, error = null) }
            }
            runCatching {
                val statusDeferred = async { repository.getStatus() }
                val rulesDeferred = async { repository.listRules() }
                val modulesDeferred = async { repository.listModules() }
                val exclusionsDeferred = async { repository.listExclusions() }

                val status = statusDeferred.await()
                val rules = rulesDeferred.await()
                val modules = modulesDeferred.await()
                val exclusions = exclusionsDeferred.await()
                val (moduleRules, custom) = groupRules(rules)

                NoMountUiState(
                    version = status?.version.orEmpty(),
                    supported = status != null,
                    rules = rules,
                    moduleRules = moduleRules,
                    customRules = custom,
                    modules = modules,
                    exclusions = exclusions,
                    loadedTabs = setOf(0, 1, 2),
                )
            }.onSuccess { newState ->
                mutableState.value = newState
            }.onFailure { e ->
                mutableState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun dispatch(action: NoMountUiAction) {
        when (action) {
            NoMountUiAction.Refresh -> refreshAll()

            is NoMountUiAction.AddRule -> {
                viewModelScope.launch {
                    if (repository.addRule(action.virtual, action.real)) {
                        invalidateAndReload()
                    }
                }
            }

            is NoMountUiAction.RemoveRule -> {
                viewModelScope.launch {
                    if (repository.removeRule(action.virtual)) {
                        invalidateAndReload()
                    }
                }
            }

            NoMountUiAction.ClearRules -> {
                viewModelScope.launch {
                    // 只清自定义规则 (remove-many), 不动模块注入规则
                    val customVirtuals = mutableState.value.customRules.map { it.virtual }
                    if (repository.removeRules(customVirtuals)) {
                        invalidateAndReload()
                    }
                }
            }

            is NoMountUiAction.LoadModule -> {
                viewModelScope.launch {
                    if (repository.loadModule(action.moduleId)) {
                        invalidateAndReload()
                    }
                }
            }

            is NoMountUiAction.UnloadModule -> {
                viewModelScope.launch {
                    if (repository.unloadModule(action.moduleId)) {
                        invalidateAndReload()
                    }
                }
            }

            is NoMountUiAction.AddExclusion -> {
                viewModelScope.launch {
                    if (repository.addExclusion(action.uid)) {
                        invalidateAndReload()
                    }
                }
            }

            is NoMountUiAction.RemoveExclusion -> {
                viewModelScope.launch {
                    if (repository.removeExclusion(action.uid)) {
                        invalidateAndReload()
                    }
                }
            }
        }
    }

    private fun invalidateAndReload() {
        mutableState.update { it.copy(loadedTabs = emptySet()) }
        refreshAll()
    }

    private fun groupRules(rules: List<NoMountRule>): Pair<List<NoMountModuleRules>, List<NoMountRule>> {
        val modulePrefix = "/data/adb/modules/"
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
        return moduleMap.map { (name, rs) -> NoMountModuleRules(name, rs) } to custom
    }
}
