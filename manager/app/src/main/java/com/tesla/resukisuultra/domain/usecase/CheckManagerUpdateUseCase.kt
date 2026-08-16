package com.tesla.resukisuultra.domain.usecase

import com.tesla.resukisuultra.data.update.ManagerUpdateRepository
import com.tesla.resukisuultra.domain.model.ManagerUpdateChannel
import com.tesla.resukisuultra.domain.model.ManagerUpdateInfo

class CheckManagerUpdateUseCase(
    private val repository: ManagerUpdateRepository,
) {
    suspend operator fun invoke(channel: ManagerUpdateChannel): ManagerUpdateInfo? =
        when (channel) {
            ManagerUpdateChannel.STABLE -> repository.checkStableUpdate()
            ManagerUpdateChannel.BETA -> repository.checkBetaUpdate()
        }
}
