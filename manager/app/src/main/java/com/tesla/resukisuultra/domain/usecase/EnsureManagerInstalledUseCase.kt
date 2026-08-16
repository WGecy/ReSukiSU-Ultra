package com.tesla.resukisuultra.domain.usecase

import com.tesla.resukisuultra.data.application.ApplicationControlRepository

class EnsureManagerInstalledUseCase(
    private val repository: ApplicationControlRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.ensureManagerInstalled()
}

