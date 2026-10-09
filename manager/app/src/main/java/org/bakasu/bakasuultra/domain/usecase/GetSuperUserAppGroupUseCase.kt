package org.bakasu.bakasuultra.domain.usecase

import org.bakasu.bakasuultra.data.packageinfo.SuperUserRepository

class GetSuperUserAppGroupUseCase(private val repository: SuperUserRepository) {
    suspend operator fun invoke(uid: Int, primaryPackageName: String) = repository.getAppGroup(uid, primaryPackageName)
}
