package com.tesla.resukisuultra.domain.usecase

import com.tesla.resukisuultra.data.startup.StartupRepository

class ObserveStartupStateUseCase(
    private val repository: StartupRepository,
) {
    operator fun invoke() = repository.state
}
