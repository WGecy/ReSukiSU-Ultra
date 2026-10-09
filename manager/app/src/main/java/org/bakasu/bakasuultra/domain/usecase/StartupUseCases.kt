package org.bakasu.bakasuultra.domain.usecase

import org.bakasu.bakasuultra.data.startup.StartupRepository

class ObserveStartupStateUseCase(
    private val repository: StartupRepository,
) {
    operator fun invoke() = repository.state
}
