package com.tesla.resukisuultra.domain.usecase

import com.tesla.resukisuultra.data.flash.FlashRepository
import com.tesla.resukisuultra.domain.model.FlashOperation

class ExecuteFlashOperationUseCase(private val repository: FlashRepository) {
    operator fun invoke(operation: FlashOperation) = repository.execute(operation)
}
