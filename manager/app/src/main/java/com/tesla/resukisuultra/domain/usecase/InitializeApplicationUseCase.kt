package com.tesla.resukisuultra.domain.usecase

import com.tesla.resukisuultra.data.AppSettingsRepository
import com.tesla.resukisuultra.data.startup.ApplicationInitializationRepository
import com.tesla.resukisuultra.data.startup.StartupRepository

class InitializeApplicationUseCase(
    private val settingsRepository: AppSettingsRepository,
    private val startupRepository: StartupRepository,
    private val initializationRepository: ApplicationInitializationRepository,
) {
    suspend operator fun invoke() {
        runCatching {
            settingsRepository.preload()
            initializationRepository.initialize()
        }.onSuccess {
            startupRepository.markReady()
        }.onFailure { error ->
            startupRepository.markFailed(error)
        }
    }
}
