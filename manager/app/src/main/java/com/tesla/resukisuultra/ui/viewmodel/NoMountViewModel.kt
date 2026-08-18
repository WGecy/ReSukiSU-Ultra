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

    /** 按 tab 懒加载 (仿 SUSFS: 进入页面/切 tab 才读取数据) — 避免进入页面卡顿 */
    fun loadTab(tab: Int) {
        val current = mutableState.value
        if (tab in current.loadedTabs || current.isLoading) return
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                when (tab) {
                    0 -> {
                        val status = repository.getStatus()
                        val modules = repository.listModules()
                        NoMountUiState(
                            version = status?.version.orEmpty(),
                            supported = status != null,
                            modules = modules,
                            loadedTabs = current.loadedTabs + 0,
                        )
                    }

                    1 -> {
                        val rules = repository.listRules()
                        val (moduleRules, custom) = groupRules(rules)
                        NoMountUiState(
                            rules = rules,
                            moduleRules = moduleRules,
                            customRules = custom,
                            loadedTabs = current.loadedTabs + 1,
                        )
                    }

                    else -> {
                        val exclusions = repository.listExclusions()
                        NoMountUiState(
                            exclusions = exclusions,
                            loadedTabs = current.loadedTabs + 2,
                        )
                    }
                }
            }.onSuccess { partial ->
                mutableState.update { it.copy(
                    version = partial.version,
                    supported = partial.supported,
                    rules = partial.rules,
                    moduleRules = partial.moduleRules,
                    customRules = partial.customRules,
                    modules = partial.modules,
                    exclusions = partial.exclusions,
                    loadedTabs = partial.loadedTabs,
                    isLoading = false,
                ) }
            }.onFailure { e ->
                mutableState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun dispatch(action: NoMountUiAction) {
        when (action) {
            NoMountUiAction.Refresh -> {
                mutableState.update { it.copy(loadedTabs = emptySet()) }
                loadTab(0)
            }

            is NoMountUiAction.AddRule -> {
                viewModelScope.launch {
                    if (repository.addRule(action.virtual, action.real)) {
                        invalidateAndReload(1)
                    }
                }
            }

            is NoMountUiAction.RemoveRule -> {
                viewModelScope.launch {
                    if (repository.removeRule(action.virtual)) {
                        invalidateAndReload(1)
                    }
                }
            }

            NoMountUiAction.ClearRules -> {
                viewModelScope.launch {
                    // 只清自定义规则 (remove-many), 不动模块注入规则
                    val customVirtuals = mutableState.value.customRules.map { it.virtual }
                    if (repository.removeRules(customVirtuals)) {
                        invalidateAndReload(1)
                    }
                }
            }

            is NoMountUiAction.LoadModule -> {
                viewModelScope.launch {
                    if (repository.loadModule(action.moduleId)) {
                        invalidateAndReload(0)
                    }
                }
            }

            is NoMountUiAction.UnloadModule -> {
                viewModelScope.launch {
                    if (repository.unloadModule(action.moduleId)) {
                        invalidateAndReload(0)
                    }
                }
            }

            is NoMountUiAction.AddExclusion -> {
                viewModelScope.launch {
                    if (repository.addExclusion(action.uid)) {
                        invalidateAndReload(2)
                    }
                }
            }

            is NoMountUiAction.RemoveExclusion -> {
                viewModelScope.launch {
                    if (repository.removeExclusion(action.uid)) {
                        invalidateAndReload(2)
                    }
                }
            }
        }
    }

    private fun invalidateAndReload(tab: Int) {
        mutableState.update { it.copy(loadedTabs = emptySet()) }
        loadTab(tab)
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
