package com.tesla.resukisuultra.data.nomount

import android.util.Log
import com.tesla.resukisuultra.data.shell.KsuCliRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class NoMountHelper(
    private val ksuCliRepository: KsuCliRepository,
) {
    private val TAG = "NoMountHelper"

    // 常驻 root shell (复用, 避免每次命令新建 su 会话 — 卡顿根因)
    @Volatile
    private var globalShell: Shell? = null
    private fun rootShell(): Shell {
        globalShell?.let { return it }
        return synchronized(this) {
            globalShell ?: ksuCliRepository.getRootShell().also { globalShell = it }
        }
    }

    private data class CommandResult(
        val success: Boolean,
        val stdout: String,
        val stderr: String,
    )

    /** 内核是否支持 NoMount (netlink 接口响应 + ksud 子命令可用) */
    suspend fun isSupported(): Boolean = getStatus() != null

    suspend fun getStatus(): NoMountStatus? {
        val result = execNomount("status")
        if (!result.success || result.stdout.isBlank()) return null
        return runCatching {
            // ksud 输出两行: supported: true / version: N — 取 version 值
            val version = result.stdout.lineSequence()
                .firstOrNull { it.startsWith("version:") }
                ?.substringAfter("version:")
                ?.trim()
                ?: return@runCatching null
            NoMountStatus(version = version)
        }.getOrNull()
    }

    suspend fun listRules(): List<NoMountRule> {
        val result = execNomount("list --json")
        if (!result.success || result.stdout.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(result.stdout)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        NoMountRule(
                            virtual = obj.optString("virtual"),
                            real = obj.optString("real"),
                        )
                    )
                }
            }
        }.getOrElse {
            Log.e(TAG, "解析 nomount list 失败", it)
            emptyList()
        }
    }

    suspend fun addRule(virtual: String, real: String): Boolean {
        if (virtual.isBlank() || real.isBlank()) return false
        return execNomount("add ${shellQuote(virtual)} ${shellQuote(real)}").success
    }

    suspend fun removeRule(virtual: String): Boolean {
        if (virtual.isBlank()) return false
        return execNomount("remove ${shellQuote(virtual)}").success
    }

    suspend fun clearRules(): Boolean = execNomount("clear").success

    /** 批量移除规则 (清空自定义用 — 不动模块注入规则) */
    suspend fun removeRules(virtualPaths: List<String>): Boolean {
        if (virtualPaths.isEmpty()) return true
        val args = virtualPaths.joinToString(" ") { shellQuote(it) }
        return execNomount("remove-many $args").success
    }

    suspend fun listModules(): List<NoMountModule> {
        val result = execNomount("modules --json")
        if (!result.success || result.stdout.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(result.stdout)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        NoMountModule(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            version = obj.optString("version"),
                            author = obj.optString("author"),
                            description = obj.optString("description"),
                            disabled = obj.optBoolean("disabled"),
                            fileCount = obj.optInt("file_count"),
                            loaded = obj.optInt("loaded"),
                        )
                    )
                }
            }
        }.getOrElse {
            Log.e(TAG, "解析 nomount modules 失败", it)
            emptyList()
        }
    }

    suspend fun loadModule(moduleId: String): Boolean {
        if (moduleId.isBlank()) return false
        return execNomount("load ${shellQuote(moduleId)}").success
    }

    suspend fun unloadModule(moduleId: String): Boolean {
        if (moduleId.isBlank()) return false
        return execNomount("unload ${shellQuote(moduleId)}").success
    }

    suspend fun listExclusions(): List<Long> {
        val result = execNomount("exclude-list --json")
        if (!result.success || result.stdout.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(result.stdout)
            buildList {
                for (i in 0 until array.length()) {
                    array.optLong(i).takeIf { it >= 0 }?.let { add(it) }
                }
            }
        }.getOrElse {
            Log.e(TAG, "解析 exclude-list 失败", it)
            emptyList()
        }
    }

    suspend fun addExclusion(uid: Long): Boolean {
        if (uid < 0) return false
        return execNomount("exclude-add $uid").success
    }

    suspend fun removeExclusion(uid: Long): Boolean {
        if (uid < 0) return false
        return execNomount("exclude-remove $uid").success
    }

    private suspend fun execNomount(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val stdout = ArrayList<String>()
            val stderr = ArrayList<String>()
            // 复用常驻 shell (不再每次 withNewRootShell 新建 su 会话)
            val result = rootShell().newJob()
                .add("${shellQuote(ksuCliRepository.getKsuDaemonPath())} nomount $command")
                .to(stdout, stderr)
                .exec()
            if (!result.isSuccess) {
                // shell 可能失效 → 重建
                globalShell = null
            }
            CommandResult(
                success = result.isSuccess,
                stdout = stdout.joinToString("\n").trim(),
                stderr = stderr.joinToString("\n").trim(),
            )
        } catch (e: Exception) {
            globalShell = null
            Log.e(TAG, "nomount command failed: $command", e)
            CommandResult(false, "", e.message.orEmpty())
        }
    }

    private fun shellQuote(value: String): String =
        "'${value.replace("'", "'\"'\"'")}'"
}

data class NoMountStatus(val version: String)

data class NoMountRule(val virtual: String, val real: String)

data class NoMountModule(
    val id: String,
    val name: String,
    val version: String = "",
    val author: String = "",
    val description: String = "",
    val disabled: Boolean,
    val fileCount: Int,
    val loaded: Int,
)
