package com.tesla.resukisuultra.data.nomount

import android.util.Log
import com.tesla.resukisuultra.data.shell.KsuCliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class NoMountHelper(
    private val ksuCliRepository: KsuCliRepository,
) {
    private val TAG = "NoMountHelper"

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
            val version = result.stdout.lineSequence().firstOrNull { it.isNotBlank() } ?: return@runCatching null
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

    private suspend fun execNomount(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val stdout = ArrayList<String>()
            val stderr = ArrayList<String>()
            val result = ksuCliRepository.withNewRootShell {
                newJob()
                    .add("${shellQuote(ksuCliRepository.getKsuDaemonPath())} nomount $command")
                    .to(stdout, stderr)
                    .exec()
            }
            CommandResult(
                success = result.isSuccess,
                stdout = stdout.joinToString("\n").trim(),
                stderr = stderr.joinToString("\n").trim(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "nomount command failed: $command", e)
            CommandResult(false, "", e.message.orEmpty())
        }
    }

    private fun shellQuote(value: String): String =
        "'${value.replace("'", "'\"'\"'")}'"
}

data class NoMountStatus(val version: String)

data class NoMountRule(val virtual: String, val real: String)
