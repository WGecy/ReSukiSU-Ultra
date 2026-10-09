package org.bakasu.bakasuultra.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.bakasu.bakasuultra.data.download.DownloadRepository
import org.bakasu.bakasuultra.domain.model.DownloadState
import org.bakasu.bakasuultra.domain.model.ManagerUpdateInfo

class EnqueueDownloadUseCase(private val repository: DownloadRepository) {
    operator fun invoke(url: String, fileName: String): Int = repository.enqueue(url, fileName)
}

class EnqueueManagerUpdateUseCase(private val repository: DownloadRepository) {
    operator fun invoke(update: ManagerUpdateInfo): Int = repository.enqueueManagerUpdate(update)
}

class ObserveDownloadUseCase(private val repository: DownloadRepository) {
    operator fun invoke(id: Int): Flow<DownloadState?> = repository.downloads.map { it[id] }
}
