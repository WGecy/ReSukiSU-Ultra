package com.tesla.resukisuultra.data.system

import android.content.Context
import com.tesla.resukisuultra.R
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import java.io.File

fun isSELinuxPermissive(): Boolean {
    val shell = Shell.Builder.create().build("sh")
    val stdoutList = ArrayList<String>()
    val result = shell.use {
        it.newJob().add("getenforce").to(stdoutList).exec()
    }
    return result.isSuccess && stdoutList.joinToString("").trim() == "Permissive"
}

/**
 * 获取 SELinux 状态 (健壮版):
 * 1. 普通 File 直接读 /sys/fs/selinux/enforce (不依赖 su shell, 避免 su 通道波动误报"已禁用")
 * 2. getenforce 命令兜底
 * 3. SuFile 最后兜底
 */
fun getSELinuxStatus(context: Context): String {
    val disabled = context.getString(R.string.selinux_status_disabled)
    val enforcing = context.getString(R.string.selinux_status_enforcing)
    val permissive = context.getString(R.string.selinux_status_permissive)
    val unknown = context.getString(R.string.unknown)

    // 1) 普通文件直接读
    val direct = runCatching {
        val f = File("/sys/fs/selinux/enforce")
        if (f.exists()) f.readText().trim().toIntOrNull() else null
    }.getOrNull()

    if (direct != null) {
        return when (direct) {
            1 -> enforcing
            0 -> permissive
            else -> unknown
        }
    }

    // 2) getenforce 命令兜底
    val permissiveByCmd = runCatching { isSELinuxPermissive() }.getOrDefault(false)
    val enforcingByCmd = runCatching {
        val shell = Shell.Builder.create().build("sh")
        val out = ArrayList<String>()
        shell.use { it.newJob().add("getenforce").to(out).exec() }
        out.joinToString("").trim() == "Enforcing"
    }.getOrDefault(false)

    return when {
        permissiveByCmd -> permissive
        enforcingByCmd -> enforcing
        else -> {
            // 3) SuFile 最后兜底
            runCatching {
                SuFile("/sys/fs/selinux/enforce").run {
                    if (exists() && canRead()) {
                        when (newInputStream().bufferedReader().use { it.readLine()?.trim()?.toIntOrNull() }) {
                            1 -> enforcing
                            0 -> permissive
                            else -> unknown
                        }
                    } else if (exists()) {
                        enforcing
                    } else {
                        disabled
                    }
                }
            }.getOrDefault(unknown)
        }
    }
}
