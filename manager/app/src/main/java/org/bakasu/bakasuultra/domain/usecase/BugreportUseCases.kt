package org.bakasu.bakasuultra.domain.usecase

import java.io.File
import org.bakasu.bakasuultra.data.logging.BugreportRepository

class GenerateBugreportUseCase(
    private val repository: BugreportRepository,
) {
    operator fun invoke(): File = repository.create()
}
