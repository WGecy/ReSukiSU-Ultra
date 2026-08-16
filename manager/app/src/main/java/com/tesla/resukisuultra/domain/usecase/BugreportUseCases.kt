package com.tesla.resukisuultra.domain.usecase

import com.tesla.resukisuultra.data.logging.BugreportRepository
import java.io.File

class GenerateBugreportUseCase(
    private val repository: BugreportRepository,
) {
    operator fun invoke(): File = repository.create()
}
