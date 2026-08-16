package com.tesla.resukisuultra.data.fakelock

import android.content.Context
import com.tesla.resukisuultra.data.shell.KsuCliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 伪装 BL 锁状态 (仿 FolkPatch fpd -hide / 8e_fake_lock.sh)
 * 通过 resetprop 隐藏 bootloader 解锁状态
 */
class FakeLockRepository(
    private val context: Context,
    private val ksuCli: KsuCliRepository,
) {
    companion object {
        const val FLAG_FILE = "/data/adb/ksu/fakelock/enabled"
        const val RESETPROP = "/data/adb/ksu/bin/resetprop"

        // 属性伪装列表 (FolkPatch prop_patch + 8e_fake_lock)
        val PATCH_LIST = listOf(
            "ro.boot.vbmeta.device_state" to "locked",
            "ro.boot.verifiedbootstate" to "green",
            "ro.boot.flash.locked" to "1",
            "ro.boot.veritymode" to "enforcing",
            "vendor.boot.vbmeta.device_state" to "locked",
            "vendor.boot.verifiedbootstate" to "green",
            "ro.boot.vbmeta.invalidate_on_error" to "yes",
            "ro.boot.vbmeta.avb_version" to "1.0",
            "ro.boot.vbmeta.hash_alg" to "sha256",
            "ro.boot.vbmeta.size" to "4096",
            "ro.boot.warranty_bit" to "0",
            "ro.warranty_bit" to "0",
            "ro.vendor.boot.warranty_bit" to "0",
            "ro.vendor.warranty_bit" to "0",
            "sys.oem_unlock_allowed" to "0",
            "ro.build.type" to "user",
            "ro.build.tags" to "release-keys",
            "ro.secureboot.lockstate" to "locked",
            "ro.debuggable" to "0",
            "ro.force.debuggable" to "0",
            "ro.secure" to "1",
            "ro.adb.secure" to "1",
            "ro.boot.realmebootstate" to "green",
            "ro.boot.realme.lockstate" to "1",
            "persist.logd.size" to "",
            "persist.logd.size.crash" to "",
            "persist.logd.size.system" to "",
            "persist.logd.size.main" to "",
        )
    }

    /** 是否已启用 (标志文件存在) */
    suspend fun isEnabled(): Boolean = withContext(Dispatchers.IO) {
        ksuCli.exec("test -f $FLAG_FILE && echo yes")?.contains("yes") == true
    }

    /** 启用/禁用伪装 */
    suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        ksuCli.exec("mkdir -p /data/adb/ksu/fakelock")
        if (enabled) {
            // 逐个设置属性 (空值属性跳过: 会破坏 shell 命令)
            for ((key, value) in PATCH_LIST) {
                if (value.isBlank()) continue
                ksuCli.exec("$RESETPROP -n \"$key\" \"$value\"")
            }
            ksuCli.exec("touch $FLAG_FILE")
        } else {
            ksuCli.exec("rm -f $FLAG_FILE")
        }
    }
}
