package com.tesla.resukisuultra.magica

import android.content.Context
import android.util.Log
import com.tesla.resukisuultra.data.AppSettingsRepository
import com.tesla.resukisuultra.data.shell.KsuCliRepository
import com.tesla.resukisuultra.ui.viewmodel.IoSchedulerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 开机应用 IO 调度器配置固化 (管理器选择 → 开机 root 写 sysfs)
 * 由 BootCompletedReceiver (主进程, Koin 已初始化) 调用
 */
object IoSchedBootApplier : KoinComponent {
    private const val TAG = "IoSchedBootApplier"
    private val settingsRepository by inject<AppSettingsRepository>()
    private val ksuCli by inject<KsuCliRepository>()

    fun apply(context: Context) {
        val saved = settingsRepository.getString(IoSchedulerViewModel.PREF_KEY)
            ?.takeIf { it.isNotBlank() }
            ?: return

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // 开机早期 su/ksud 可能未就绪: 延迟 + 重试
            delay(5000)
            repeat(3) {
                if (applyScheduler(saved)) return@launch
                delay(5000)
            }
            Log.w(TAG, "应用 IO 调度器 $saved 失败 (3 次重试)")
        }
    }

    private fun applyScheduler(name: String): Boolean {
        val blocks = ksuCli.exec("ls /sys/block/")
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.startsWith("sd") || it.startsWith("nvme") }
            ?: return false
        var success = false
        for (dev in blocks) {
            if (ksuCli.exec("echo $name > /sys/block/$dev/queue/scheduler") != null) {
                success = true
            }
        }
        return success
    }
}
