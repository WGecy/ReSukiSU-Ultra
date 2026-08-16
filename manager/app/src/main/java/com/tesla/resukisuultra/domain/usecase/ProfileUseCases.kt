package com.tesla.resukisuultra.domain.usecase

import com.tesla.resukisuultra.data.profile.ProfileRepository
import com.tesla.resukisuultra.domain.model.AppProfile

class GetAppProfileUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(packageName: String, uid: Int) =
        repository.getProfile(packageName, uid)
}

class SetAppProfileUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(profile: AppProfile) = repository.setProfile(profile)
}

class GetDefaultUmountModulesUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke() = repository.isDefaultUmountModules()
}
