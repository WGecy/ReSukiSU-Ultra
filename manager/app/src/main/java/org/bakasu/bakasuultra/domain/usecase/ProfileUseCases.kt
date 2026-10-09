package org.bakasu.bakasuultra.domain.usecase

import org.bakasu.bakasuultra.data.profile.ProfileRepository
import org.bakasu.bakasuultra.domain.model.AppProfile

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
