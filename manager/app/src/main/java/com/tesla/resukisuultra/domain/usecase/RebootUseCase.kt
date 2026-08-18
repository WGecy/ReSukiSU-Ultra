package com.tesla.resukisuultra.domain.usecase

import com.tesla.resukisuultra.Natives
import com.tesla.resukisuultra.data.AppSettingsRepository
import com.tesla.resukisuultra.data.application.ApplicationControlRepository

/** 软重启偏好: 晚加载(jailbreak)模式或设置里开启软重启 */
fun isSoftRebootPreferred(settings: AppSettingsRepository): Boolean =
    runCatching { Natives.isLateLoadMode }.getOrDefault(false) ||
        settings.getBoolean(KEY_USE_SOFT_REBOOT, false)

const val KEY_USE_SOFT_REBOOT = "soft_reboot"

class RebootUseCase(
    private val repository: ApplicationControlRepository,
    private val settingsRepository: AppSettingsRepository,
) {
    suspend operator fun invoke(reason: String = ""): Result<Unit> {
        // 软重启保留 jailbreak 且仍应用模块改动; 完整重启会掉 jailbreak
        val finalReason = if (reason.isEmpty() && isSoftRebootPreferred(settingsRepository)) {
            "soft_reboot"
        } else {
            reason
        }
        return repository.reboot(finalReason)
    }
}
