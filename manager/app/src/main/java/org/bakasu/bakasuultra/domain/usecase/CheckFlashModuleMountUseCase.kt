package org.bakasu.bakasuultra.domain.usecase

import org.bakasu.bakasuultra.data.flash.FlashRepository

class CheckFlashModuleMountUseCase(private val repository: FlashRepository) {
    suspend operator fun invoke(uri: String) = repository.moduleNeedsMount(uri)
}
