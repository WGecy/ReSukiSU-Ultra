package org.bakasu.bakasuultra.domain.usecase

import org.bakasu.bakasuultra.data.update.ManagerUpdateRepository
import org.bakasu.bakasuultra.domain.model.ManagerUpdateChannel
import org.bakasu.bakasuultra.domain.model.ManagerUpdateInfo

class CheckManagerUpdateUseCase(
    private val repository: ManagerUpdateRepository,
) {
    suspend operator fun invoke(channel: ManagerUpdateChannel): ManagerUpdateInfo? =
        when (channel) {
            ManagerUpdateChannel.STABLE -> repository.checkStableUpdate()
            ManagerUpdateChannel.BETA -> repository.checkBetaUpdate()
        }
}
