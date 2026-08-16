package com.tesla.resukisuultra.domain.usecase

import com.tesla.resukisuultra.data.packageinfo.SuperUserRepository

class GetSuperUserAppGroupUseCase(private val repository: SuperUserRepository) {
    suspend operator fun invoke(uid: Int, primaryPackageName: String) =
        repository.getAppGroup(uid, primaryPackageName)
}
