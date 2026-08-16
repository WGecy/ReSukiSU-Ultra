package com.tesla.resukisuultra.data.netisolate

import android.content.Context
import com.tesla.resukisuultra.data.shell.KsuCliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 联网隔离数据仓库
 * 持久化: /data/adb/ksu/netisolate/{enabled,uids}
 * (仿 FolkPatch: 文件持久化, 内核侧读取)
 */
class NetIsolateRepository(
    private val context: Context,
    private val ksuCli: KsuCliRepository,
) {
    companion object {
        const val NETISOLATE_DIR = "/data/adb/ksu/netisolate/"
        const val ENABLE_FILE = "/data/adb/ksu/netisolate/enabled"
        const val UIDS_FILE = "/data/adb/ksu/netisolate/uids"
    }

    /** 读取是否启用 */
    suspend fun isEnabled(): Boolean = withContext(Dispatchers.IO) {
        ksuCli.exec("cat $ENABLE_FILE")?.trim() == "1"
    }

    /** 设置启用/禁用 (持久化 + 实时同步内核) */
    suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        ksuCli.exec("mkdir -p $NETISOLATE_DIR")
        ksuCli.exec("echo ${if (enabled) 1 else 0} > $ENABLE_FILE")
        syncToKernel()
    }

    /** 读取 UID 列表 */
    suspend fun getUids(): Set<Int> = withContext(Dispatchers.IO) {
        ksuCli.exec("cat $UIDS_FILE")
            ?.split("\n")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    /** 写入 UID 列表 (持久化 + 实时同步内核) */
    suspend fun setUids(uids: Set<Int>) = withContext(Dispatchers.IO) {
        ksuCli.exec("mkdir -p $NETISOLATE_DIR")
        val content = uids.joinToString("\n")
        ksuCli.exec("echo '$content' > $UIDS_FILE")
        syncToKernel()
    }

    /** 调 ksud netisolate 命令: 读配置文件 → supercall 实时应用到内核 */
    private suspend fun syncToKernel() {
        ksuCli.execKsud("netisolate", newShell = true)
    }
}
