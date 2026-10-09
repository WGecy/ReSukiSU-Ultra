package org.bakasu.bakasuultra.domain.usecase

import org.bakasu.bakasuultra.data.AppSettingsRepository
import org.bakasu.bakasuultra.data.startup.ApplicationInitializationRepository
import org.bakasu.bakasuultra.data.startup.StartupRepository

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
