package org.bakasu.bakasuultra.domain.usecase

import org.bakasu.bakasuultra.data.flash.FlashRepository
import org.bakasu.bakasuultra.domain.model.InstallEnvironment

class GetInstallEnvironmentUseCase(
    private val repository: FlashRepository,
) {
    fun cached(): InstallEnvironment? = repository.installEnvironment.value

    suspend operator fun invoke(forceRefresh: Boolean = false) = repository.getInstallEnvironment(forceRefresh)
}
