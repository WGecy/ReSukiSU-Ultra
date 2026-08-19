package com.tesla.resukisuultra.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tesla.resukisuultra.data.shell.KsuCliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class KpmModuleInfo(
    val name: String,
    val version: String = "",
    val author: String = "",
    val description: String = "",
)

data class KpmUiState(
    val modules: List<KpmModuleInfo> = emptyList(),
    val loaded: Boolean = false,
)

class KpmViewModel(
    private val ksuCli: KsuCliRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(KpmUiState())
    val uiState: StateFlow<KpmUiState> = mutableState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val modules = withContext(Dispatchers.IO) { readModules() }
            mutableState.value = KpmUiState(modules = modules, loaded = true)
        }
    }

    fun unload(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ksuCli.exec("${ksuCli.getKsuDaemonPath()} kpm unload $name")
            }
            refresh()
        }
    }

    private fun readModules(): List<KpmModuleInfo> {
        val out = ksuCli.exec("${ksuCli.getKsuDaemonPath()} kpm list") ?: return emptyList()
        // 解析: kpm list 输出 (name, version, author...) — 兼容 INI 段或简单行
        return out.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                if (line.startsWith("name=")) {
                    KpmModuleInfo(name = line.substringAfter("name="))
                } else {
                    null
                }
            }.toList()
    }
}
