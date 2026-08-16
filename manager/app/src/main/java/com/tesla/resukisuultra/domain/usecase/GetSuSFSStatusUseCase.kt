package com.tesla.resukisuultra.domain.usecase

import com.tesla.resukisuultra.data.susfs.SuSFSRepository

class GetSuSFSStatusUseCase(private val repository: SuSFSRepository) {
    suspend operator fun invoke() = repository.getStatus()
}

