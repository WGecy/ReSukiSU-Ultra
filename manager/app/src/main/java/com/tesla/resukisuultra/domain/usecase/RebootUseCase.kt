package com.tesla.resukisuultra.domain.usecase

import com.tesla.resukisuultra.data.application.ApplicationControlRepository

class RebootUseCase(
    private val repository: ApplicationControlRepository,
) {
    suspend operator fun invoke(reason: String = "") = repository.reboot(reason)
}
