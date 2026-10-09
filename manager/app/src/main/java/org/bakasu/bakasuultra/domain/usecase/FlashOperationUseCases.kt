package org.bakasu.bakasuultra.domain.usecase

import org.bakasu.bakasuultra.data.flash.FlashRepository
import org.bakasu.bakasuultra.domain.model.FlashOperation

class ExecuteFlashOperationUseCase(private val repository: FlashRepository) {
    operator fun invoke(operation: FlashOperation) = repository.execute(operation)
}
