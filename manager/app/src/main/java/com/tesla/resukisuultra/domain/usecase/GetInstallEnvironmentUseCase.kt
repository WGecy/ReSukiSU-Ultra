package com.tesla.resukisuultra.domain.usecase

import com.tesla.resukisuultra.data.flash.FlashRepository
import com.tesla.resukisuultra.domain.model.InstallEnvironment

class GetInstallEnvironmentUseCase(
    private val repository: FlashRepository,
) {
    fun cached(): InstallEnvironment? = repository.installEnvironment.value

    suspend operator fun invoke(forceRefresh: Boolean = false) =
        repository.getInstallEnvironment(forceRefresh)
}
