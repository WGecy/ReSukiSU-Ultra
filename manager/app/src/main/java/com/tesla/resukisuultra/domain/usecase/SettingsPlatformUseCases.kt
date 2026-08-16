package com.tesla.resukisuultra.domain.usecase

import com.tesla.resukisuultra.data.settings.SettingsPlatformRepository
import com.tesla.resukisuultra.domain.model.AppearanceSetting
import com.tesla.resukisuultra.domain.model.PlatformSetting

class LoadSettingsPlatformUseCase(private val repository: SettingsPlatformRepository) {
    operator fun invoke() = repository.load()
}

class UpdateAppearanceUseCase(private val repository: SettingsPlatformRepository) {
    suspend operator fun invoke(setting: AppearanceSetting) = repository.updateAppearance(setting)
}

class UpdatePlatformSettingUseCase(private val repository: SettingsPlatformRepository) {
    operator fun invoke(setting: PlatformSetting) = repository.updatePlatform(setting)
}

class GetPlatformFeatureStatusUseCase(private val repository: SettingsPlatformRepository) {
    suspend operator fun invoke() = repository.getFeatureStatus()
}
