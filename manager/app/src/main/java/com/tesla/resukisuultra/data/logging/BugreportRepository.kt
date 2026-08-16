package com.tesla.resukisuultra.data.logging

import android.app.Application
import com.tesla.resukisuultra.data.shell.KsuCliRepository
import java.io.File

class BugreportRepository(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
) {
    fun create(): File = getBugreportFile(application, ksuCliRepository)
}
