package org.bakasu.bakasuultra.domain.usecase

import org.bakasu.bakasuultra.data.flash.FlashRepository

class ObserveKernelFlashUseCase(private val repository: FlashRepository) {
    operator fun invoke() = repository.kernelFlashSession
}

class StartKernelFlashUseCase(private val repository: FlashRepository) {
    operator fun invoke(uri: String, selectedSlot: String?, skipKsud: Boolean = false) = repository.startKernelFlash(uri, selectedSlot, skipKsud)
}
