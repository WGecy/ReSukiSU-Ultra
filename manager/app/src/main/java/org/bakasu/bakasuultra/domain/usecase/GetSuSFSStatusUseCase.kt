package org.bakasu.bakasuultra.domain.usecase

import org.bakasu.bakasuultra.data.susfs.SuSFSRepository

class GetSuSFSStatusUseCase(private val repository: SuSFSRepository) {
    suspend operator fun invoke() = repository.getStatus()
}

