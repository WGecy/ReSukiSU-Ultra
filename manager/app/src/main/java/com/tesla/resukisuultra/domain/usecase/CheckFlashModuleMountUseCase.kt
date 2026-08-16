package com.tesla.resukisuultra.domain.usecase

import com.tesla.resukisuultra.data.flash.FlashRepository

class CheckFlashModuleMountUseCase(private val repository: FlashRepository) {
    suspend operator fun invoke(uri: String) = repository.moduleNeedsMount(uri)
}
