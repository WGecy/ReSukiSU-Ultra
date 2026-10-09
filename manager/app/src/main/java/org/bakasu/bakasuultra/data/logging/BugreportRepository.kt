package org.bakasu.bakasuultra.data.logging

import android.app.Application
import java.io.File
import org.bakasu.bakasuultra.data.shell.KsuCliRepository

class BugreportRepository(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
) {
    fun create(): File = getBugreportFile(application, ksuCliRepository)
}
