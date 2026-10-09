package org.bakasu.bakasuultra.di

import coil.ImageLoader
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.bakasu.bakasuultra.BuildConfig
import org.bakasu.bakasuultra.data.AppSettingsRepository
import org.bakasu.bakasuultra.data.application.ApplicationControlRepository
import org.bakasu.bakasuultra.data.application.DynamicManagerRepository
import org.bakasu.bakasuultra.data.count.CountRepository
import org.bakasu.bakasuultra.data.download.DownloadRepository
import org.bakasu.bakasuultra.data.file.ModuleFileRepository
import org.bakasu.bakasuultra.data.flash.FlashRepository
import org.bakasu.bakasuultra.data.kernel.KernelRepository
import org.bakasu.bakasuultra.data.kernel.UmountRepository
import org.bakasu.bakasuultra.data.logging.BugreportRepository
import org.bakasu.bakasuultra.data.logging.SulogRepository
import org.bakasu.bakasuultra.data.module.ModuleActionRepository
import org.bakasu.bakasuultra.data.module.ModuleCatalogRepository
import org.bakasu.bakasuultra.data.module.ModulePreferencesRepository
import org.bakasu.bakasuultra.data.module.ModuleRepository
import org.bakasu.bakasuultra.data.netisolate.NetIsolateRepository
import org.bakasu.bakasuultra.data.network.NetworkRequestRepository
import org.bakasu.bakasuultra.data.network.NetworkStatusRepository
import org.bakasu.bakasuultra.data.network.WebResourceRepository
import org.bakasu.bakasuultra.data.packageinfo.AppIconDataSource
import org.bakasu.bakasuultra.data.packageinfo.InstalledPackageCache
import org.bakasu.bakasuultra.data.packageinfo.InstalledPackageRepository
import org.bakasu.bakasuultra.data.packageinfo.RootServiceRepository
import org.bakasu.bakasuultra.data.packageinfo.SuperUserRepository
import org.bakasu.bakasuultra.data.profile.ProfileRepository
import org.bakasu.bakasuultra.data.profile.ProfileTemplateRepository
import org.bakasu.bakasuultra.data.settings.LocaleHelper
import org.bakasu.bakasuultra.data.settings.LocaleRepository
import org.bakasu.bakasuultra.data.settings.SettingsPlatformRepository
import org.bakasu.bakasuultra.data.shell.KsuCliRepository
import org.bakasu.bakasuultra.data.shell.ShortcutRepository
import org.bakasu.bakasuultra.data.startup.ApplicationInitializationRepository
import org.bakasu.bakasuultra.data.startup.StartupRepository
import org.bakasu.bakasuultra.data.susfs.SuSFSConfigHelper
import org.bakasu.bakasuultra.data.susfs.SuSFSRepository
import org.bakasu.bakasuultra.data.system.HomeRuntimeRepository
import org.bakasu.bakasuultra.data.system.HomeStateRepository
import org.bakasu.bakasuultra.data.text.HanziToPinyin
import org.bakasu.bakasuultra.data.theme.MonetCompatColorSource
import org.bakasu.bakasuultra.data.theme.ThemeRepository
import org.bakasu.bakasuultra.data.update.ManagerUpdateRepository
import org.bakasu.bakasuultra.data.webui.WebUiRepository
import org.bakasu.bakasuultra.domain.text.TextTransliterator
import org.bakasu.bakasuultra.domain.usecase.AddUmountPathUseCase
import org.bakasu.bakasuultra.domain.usecase.ApplyLanguageUseCase
import org.bakasu.bakasuultra.domain.usecase.BackupAllowlistUseCase
import org.bakasu.bakasuultra.domain.usecase.CalculateInstalledModuleSizeUseCase
import org.bakasu.bakasuultra.domain.usecase.CheckFlashModuleMountUseCase
import org.bakasu.bakasuultra.domain.usecase.CheckManagerUpdateUseCase
import org.bakasu.bakasuultra.domain.usecase.CleanSulogUseCase
import org.bakasu.bakasuultra.domain.usecase.ClearDynamicManagerUseCase
import org.bakasu.bakasuultra.domain.usecase.ConfigureSuLogUseCase
import org.bakasu.bakasuultra.domain.usecase.ControlAppUseCase
import org.bakasu.bakasuultra.domain.usecase.DeleteProfileTemplateUseCase
import org.bakasu.bakasuultra.domain.usecase.EnableSulogUseCase
import org.bakasu.bakasuultra.domain.usecase.EnqueueDownloadUseCase
import org.bakasu.bakasuultra.domain.usecase.EnqueueManagerUpdateUseCase
import org.bakasu.bakasuultra.domain.usecase.EnsureManagerInstalledUseCase
import org.bakasu.bakasuultra.domain.usecase.ExecuteFlashOperationUseCase
import org.bakasu.bakasuultra.domain.usecase.ExecuteModuleActionUseCase
import org.bakasu.bakasuultra.domain.usecase.ExportProfileTemplatesUseCase
import org.bakasu.bakasuultra.domain.usecase.ExtractModuleIdUseCase
import org.bakasu.bakasuultra.domain.usecase.ExtractModuleNameUseCase
import org.bakasu.bakasuultra.domain.usecase.FetchRemoteTextUseCase
import org.bakasu.bakasuultra.domain.usecase.GenerateBugreportUseCase
import org.bakasu.bakasuultra.domain.usecase.GetAppProfileUseCase
import org.bakasu.bakasuultra.domain.usecase.GetAppSepolicyUseCase
import org.bakasu.bakasuultra.domain.usecase.GetBooleanPreferenceUseCase
import org.bakasu.bakasuultra.domain.usecase.GetCatalogModuleUseCase
import org.bakasu.bakasuultra.domain.usecase.GetDefaultUmountModulesUseCase
import org.bakasu.bakasuultra.domain.usecase.GetHomeBasicInfoUseCase
import org.bakasu.bakasuultra.domain.usecase.GetInstallEnvironmentUseCase
import org.bakasu.bakasuultra.domain.usecase.GetKernelFeatureSettingsUseCase
import org.bakasu.bakasuultra.domain.usecase.GetKernelStatusUseCase
import org.bakasu.bakasuultra.domain.usecase.GetManagerRuntimeInfoUseCase
import org.bakasu.bakasuultra.domain.usecase.GetPlatformFeatureStatusUseCase
import org.bakasu.bakasuultra.domain.usecase.GetProfileTemplateUseCase
import org.bakasu.bakasuultra.domain.usecase.GetStringPreferenceUseCase
import org.bakasu.bakasuultra.domain.usecase.GetStringSetPreferenceUseCase
import org.bakasu.bakasuultra.domain.usecase.GetSuSFSStatusUseCase
import org.bakasu.bakasuultra.domain.usecase.GetSuperUserAppGroupUseCase
import org.bakasu.bakasuultra.domain.usecase.ImportAllowlistUseCase
import org.bakasu.bakasuultra.domain.usecase.ImportProfileTemplatesUseCase
import org.bakasu.bakasuultra.domain.usecase.InitializeApplicationUseCase
import org.bakasu.bakasuultra.domain.usecase.IsLateLoadModeUseCase
import org.bakasu.bakasuultra.domain.usecase.IsModuleUriAccessibleUseCase
import org.bakasu.bakasuultra.domain.usecase.IsNetworkAvailableUseCase
import org.bakasu.bakasuultra.domain.usecase.IsSoftRebootPreferredUseCase
import org.bakasu.bakasuultra.domain.usecase.IsSystemLanguageSettingsUseCase
import org.bakasu.bakasuultra.domain.usecase.LaunchSystemLanguageSettingsUseCase
import org.bakasu.bakasuultra.domain.usecase.LoadSettingsPlatformUseCase
import org.bakasu.bakasuultra.domain.usecase.ObserveCatalogModulesUseCase
import org.bakasu.bakasuultra.domain.usecase.ObserveDownloadUseCase
import org.bakasu.bakasuultra.domain.usecase.ObserveDynamicManagerStateUseCase
import org.bakasu.bakasuultra.domain.usecase.ObserveInstalledModulesUseCase
import org.bakasu.bakasuultra.domain.usecase.ObserveKernelFlashUseCase
import org.bakasu.bakasuultra.domain.usecase.ObserveModuleCatalogOfflineUseCase
import org.bakasu.bakasuultra.domain.usecase.ObserveModuleCatalogRefreshingUseCase
import org.bakasu.bakasuultra.domain.usecase.ObserveProfileTemplateOfflineUseCase
import org.bakasu.bakasuultra.domain.usecase.ObserveProfileTemplateRefreshingUseCase
import org.bakasu.bakasuultra.domain.usecase.ObserveProfileTemplatesUseCase
import org.bakasu.bakasuultra.domain.usecase.ObserveStartupStateUseCase
import org.bakasu.bakasuultra.domain.usecase.ObserveSulogStateUseCase
import org.bakasu.bakasuultra.domain.usecase.ObserveSuperUserStateUseCase
import org.bakasu.bakasuultra.domain.usecase.ObserveUmountStateUseCase
import org.bakasu.bakasuultra.domain.usecase.RebootUseCase
import org.bakasu.bakasuultra.domain.usecase.RefreshDynamicManagerUseCase
import org.bakasu.bakasuultra.domain.usecase.RefreshInstalledModulesUseCase
import org.bakasu.bakasuultra.domain.usecase.RefreshModuleCatalogUseCase
import org.bakasu.bakasuultra.domain.usecase.RefreshProfileTemplatesUseCase
import org.bakasu.bakasuultra.domain.usecase.RefreshSulogUseCase
import org.bakasu.bakasuultra.domain.usecase.RefreshSuperUsersUseCase
import org.bakasu.bakasuultra.domain.usecase.RefreshUmountPathsUseCase
import org.bakasu.bakasuultra.domain.usecase.RemovePreferenceUseCase
import org.bakasu.bakasuultra.domain.usecase.RemoveUmountPathUseCase
import org.bakasu.bakasuultra.domain.usecase.SaveModuleActionLogUseCase
import org.bakasu.bakasuultra.domain.usecase.SaveProfileTemplateUseCase
import org.bakasu.bakasuultra.domain.usecase.SelectDynamicManagerUseCase
import org.bakasu.bakasuultra.domain.usecase.SetAppProfileUseCase
import org.bakasu.bakasuultra.domain.usecase.SetAppSepolicyUseCase
import org.bakasu.bakasuultra.domain.usecase.SetBooleanPreferenceUseCase
import org.bakasu.bakasuultra.domain.usecase.SetDefaultUmountModulesUseCase
import org.bakasu.bakasuultra.domain.usecase.SetKernelUmountEnabledUseCase
import org.bakasu.bakasuultra.domain.usecase.SetManualDynamicManagerUseCase
import org.bakasu.bakasuultra.domain.usecase.SetModuleEnabledUseCase
import org.bakasu.bakasuultra.domain.usecase.SetModuleRemovedUseCase
import org.bakasu.bakasuultra.domain.usecase.SetSelinuxHideEnabledUseCase
import org.bakasu.bakasuultra.domain.usecase.SetStringPreferenceUseCase
import org.bakasu.bakasuultra.domain.usecase.SetStringSetPreferenceUseCase
import org.bakasu.bakasuultra.domain.usecase.SetSuEnabledUseCase
import org.bakasu.bakasuultra.domain.usecase.StartKernelFlashUseCase
import org.bakasu.bakasuultra.domain.usecase.SuSFSConfigUseCase
import org.bakasu.bakasuultra.domain.usecase.TakeModuleUriPermissionUseCase
import org.bakasu.bakasuultra.domain.usecase.TransliterateTextUseCase
import org.bakasu.bakasuultra.domain.usecase.UpdateAppearanceUseCase
import org.bakasu.bakasuultra.domain.usecase.UpdateCachedModuleEnabledUseCase
import org.bakasu.bakasuultra.domain.usecase.UpdatePlatformSettingUseCase
import org.bakasu.bakasuultra.domain.usecase.ValidateSepolicyUseCase
import org.bakasu.bakasuultra.ui.activity.util.ThemeUtils
import org.bakasu.bakasuultra.ui.component.ZipFileDetector
import org.bakasu.bakasuultra.ui.theme.BackgroundManager
import org.bakasu.bakasuultra.ui.theme.CardConfig
import org.bakasu.bakasuultra.ui.theme.ThemeConfig
import org.bakasu.bakasuultra.ui.util.module.Shortcut
import org.bakasu.bakasuultra.ui.viewmodel.AppProfileViewModel
import org.bakasu.bakasuultra.ui.viewmodel.DynamicManagerViewModel
import org.bakasu.bakasuultra.ui.viewmodel.ExecuteModuleActionViewModel
import org.bakasu.bakasuultra.ui.viewmodel.FlashViewModel
import org.bakasu.bakasuultra.ui.viewmodel.HomeViewModel
import org.bakasu.bakasuultra.ui.viewmodel.InstallViewModel
import org.bakasu.bakasuultra.ui.viewmodel.IoSchedulerViewModel
import org.bakasu.bakasuultra.ui.viewmodel.KernelFlashViewModel
import org.bakasu.bakasuultra.ui.viewmodel.MainIntentViewModel
import org.bakasu.bakasuultra.ui.viewmodel.ModuleDetailViewModel
import org.bakasu.bakasuultra.ui.viewmodel.ModuleRepoViewModel
import org.bakasu.bakasuultra.ui.viewmodel.ModuleViewModel
import org.bakasu.bakasuultra.ui.viewmodel.NetIsolateViewModel
import org.bakasu.bakasuultra.ui.viewmodel.SettingsViewModel
import org.bakasu.bakasuultra.ui.viewmodel.SuSFSViewModel
import org.bakasu.bakasuultra.ui.viewmodel.SulogViewModel
import org.bakasu.bakasuultra.ui.viewmodel.SuperUserViewModel
import org.bakasu.bakasuultra.ui.viewmodel.TemplateEditorViewModel
import org.bakasu.bakasuultra.ui.viewmodel.TemplateViewModel
import org.bakasu.bakasuultra.ui.viewmodel.UmountManagerScreenViewModel
import org.bakasu.bakasuultra.ui.webui.MonetColorsProvider
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val applicationScopeQualifier = named("applicationScope")

val coreModule = module {
    single<CoroutineScope>(applicationScopeQualifier) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
    single {
        OkHttpClient.Builder()
            .cache(Cache(File(androidApplication().cacheDir, "okhttp"), 10L * 1024L * 1024L))
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "BakaSU/${BuildConfig.VERSION_CODE}")
                        .header("Accept-Language", Locale.getDefault().toLanguageTag())
                        .build()
                )
            }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }
    single {
        val application = androidApplication()
        val iconSize = application.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        ImageLoader.Builder(application)
            .components {
                add(AppIconKeyer())
                add(AppIconFetcher.Factory(iconSize, false, application))
            }
            .build()
    }
}

val repositoryModule = module {
    single { KsuCliRepository(androidApplication()) }
    single { NetIsolateRepository(androidApplication(), get()) }
    singleOf(::CountRepository)
    singleOf(::InstalledPackageCache)
    singleOf(::AppIconDataSource)
    singleOf(::RootServiceRepository)
    singleOf(::InstalledPackageRepository)
    single {
        SuperUserRepository(
            application = get(),
            cache = get(),
            installedPackageRepository = get(),
            profileRepository = get(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
    single {
        AppSettingsRepository(
            context = androidApplication(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
    singleOf(::StartupRepository)
    single {
        ApplicationInitializationRepository(
            application = get(),
            imageLoader = get(),
            applicationScope = get(applicationScopeQualifier),
            flashRepository = get(),
            ksuCliRepository = get(),
            monetCompatColorSource = get(),
        )
    }
    singleOf(::ManagerUpdateRepository)
    singleOf(::ApplicationControlRepository)
    singleOf(::DownloadRepository)
    single { FlashRepository(get(), get(applicationScopeQualifier), get(), get()) }
    singleOf(::KernelRepository)
    singleOf(::HomeRuntimeRepository)
    singleOf(::HomeStateRepository)
    singleOf(::NetworkStatusRepository)
    singleOf(::NetworkRequestRepository)
    singleOf(::DynamicManagerRepository)
    singleOf(::SulogRepository)
    singleOf(::BugreportRepository)
    singleOf(::UmountRepository)
    singleOf(::ModuleCatalogRepository)
    singleOf(::ModuleRepository)
    singleOf(::ModulePreferencesRepository)
    singleOf(::ModuleActionRepository)
    singleOf(::WebResourceRepository)
    singleOf(::WebUiRepository)
    singleOf(::ModuleFileRepository)
    singleOf(::ProfileRepository)
    singleOf(::ProfileTemplateRepository)
    singleOf(::SuSFSConfigHelper)
    singleOf(::SuSFSRepository)
    singleOf(::MonetCompatColorSource)
    singleOf(::ThemeRepository)
    single {
        val themeRepository = get<ThemeRepository>()
        ThemeConfig(themeRepository::defaultSeedColor)
    }
    singleOf(::CardConfig)
    singleOf(::BackgroundManager)
    singleOf(::ThemeUtils)
    singleOf(::LocaleHelper)
    singleOf(::LocaleRepository)
    singleOf(::SettingsPlatformRepository)
    singleOf(::ShortcutRepository)
    singleOf(::Shortcut)
    singleOf(::MonetColorsProvider)
    singleOf(::ZipFileDetector)
    single { HanziToPinyin.create() } bind TextTransliterator::class
}

val useCaseModule = module {
    factoryOf(::InitializeApplicationUseCase)
    factoryOf(::GetHomeBasicInfoUseCase)
    factoryOf(::IsNetworkAvailableUseCase)
    factoryOf(::LoadSettingsPlatformUseCase)
    factoryOf(::UpdateAppearanceUseCase)
    factoryOf(::UpdatePlatformSettingUseCase)
    factoryOf(::GetPlatformFeatureStatusUseCase)
    factoryOf(::IsSoftRebootPreferredUseCase)
    factoryOf(::CheckManagerUpdateUseCase)
    factoryOf(::EnsureManagerInstalledUseCase)
    factoryOf(::RebootUseCase)
    factoryOf(::EnqueueDownloadUseCase)
    factoryOf(::EnqueueManagerUpdateUseCase)
    factoryOf(::ObserveDownloadUseCase)
    factoryOf(::GetKernelStatusUseCase)
    factoryOf(::GetInstallEnvironmentUseCase)
    factoryOf(::ExecuteFlashOperationUseCase)
    factoryOf(::CheckFlashModuleMountUseCase)
    factoryOf(::GetManagerRuntimeInfoUseCase)
    factoryOf(::GetKernelFeatureSettingsUseCase)
    factoryOf(::SetSuEnabledUseCase)
    factoryOf(::SetKernelUmountEnabledUseCase)
    factoryOf(::ConfigureSuLogUseCase)
    factoryOf(::SetSelinuxHideEnabledUseCase)
    factoryOf(::SetDefaultUmountModulesUseCase)
    factoryOf(::IsLateLoadModeUseCase)
    factoryOf(::GetAppProfileUseCase)
    factoryOf(::SetAppProfileUseCase)
    factoryOf(::GetAppSepolicyUseCase)
    factoryOf(::SetAppSepolicyUseCase)
    factoryOf(::ControlAppUseCase)
    factoryOf(::ValidateSepolicyUseCase)
    factoryOf(::GetDefaultUmountModulesUseCase)
    factoryOf(::GetSuSFSStatusUseCase)
    factoryOf(::SuSFSConfigUseCase)
    factoryOf(::ApplyLanguageUseCase)
    factoryOf(::IsSystemLanguageSettingsUseCase)
    factoryOf(::LaunchSystemLanguageSettingsUseCase)
    factoryOf(::GenerateBugreportUseCase)
    factoryOf(::ObserveStartupStateUseCase)
    factoryOf(::GetSuperUserAppGroupUseCase)
    factoryOf(::ObserveCatalogModulesUseCase)
    factoryOf(::ObserveModuleCatalogRefreshingUseCase)
    factoryOf(::ObserveModuleCatalogOfflineUseCase)
    factoryOf(::RefreshModuleCatalogUseCase)
    factoryOf(::GetCatalogModuleUseCase)
    factoryOf(::ObserveProfileTemplatesUseCase)
    factoryOf(::ObserveProfileTemplateRefreshingUseCase)
    factoryOf(::ObserveProfileTemplateOfflineUseCase)
    factoryOf(::RefreshProfileTemplatesUseCase)
    factoryOf(::GetProfileTemplateUseCase)
    factoryOf(::SaveProfileTemplateUseCase)
    factoryOf(::DeleteProfileTemplateUseCase)
    factoryOf(::ImportProfileTemplatesUseCase)
    factoryOf(::ExportProfileTemplatesUseCase)
    factoryOf(::GetBooleanPreferenceUseCase)
    factoryOf(::SetBooleanPreferenceUseCase)
    factoryOf(::GetStringPreferenceUseCase)
    factoryOf(::SetStringPreferenceUseCase)
    factoryOf(::GetStringSetPreferenceUseCase)
    factoryOf(::SetStringSetPreferenceUseCase)
    factoryOf(::ObserveDynamicManagerStateUseCase)
    factoryOf(::RefreshDynamicManagerUseCase)
    factoryOf(::SelectDynamicManagerUseCase)
    factoryOf(::SetManualDynamicManagerUseCase)
    factoryOf(::ClearDynamicManagerUseCase)
    factoryOf(::ObserveSulogStateUseCase)
    factoryOf(::RefreshSulogUseCase)
    factoryOf(::EnableSulogUseCase)
    factoryOf(::CleanSulogUseCase)
    factoryOf(::ObserveUmountStateUseCase)
    factoryOf(::RefreshUmountPathsUseCase)
    factoryOf(::AddUmountPathUseCase)
    factoryOf(::RemoveUmountPathUseCase)
    factoryOf(::ObserveKernelFlashUseCase)
    factoryOf(::StartKernelFlashUseCase)
    factoryOf(::RemovePreferenceUseCase)
    factoryOf(::ObserveSuperUserStateUseCase)
    factoryOf(::RefreshSuperUsersUseCase)
    factoryOf(::BackupAllowlistUseCase)
    factoryOf(::ImportAllowlistUseCase)
    factoryOf(::FetchRemoteTextUseCase)
    factoryOf(::IsModuleUriAccessibleUseCase)
    factoryOf(::TakeModuleUriPermissionUseCase)
    factoryOf(::ExtractModuleNameUseCase)
    factoryOf(::ExtractModuleIdUseCase)
    factoryOf(::ObserveInstalledModulesUseCase)
    factoryOf(::RefreshInstalledModulesUseCase)
    factoryOf(::CalculateInstalledModuleSizeUseCase)
    factoryOf(::UpdateCachedModuleEnabledUseCase)
    factoryOf(::ExecuteModuleActionUseCase)
    factoryOf(::SaveModuleActionLogUseCase)
    factoryOf(::SetModuleEnabledUseCase)
    factoryOf(::SetModuleRemovedUseCase)
    factoryOf(::TransliterateTextUseCase)
}

val viewModelModule = module {
    viewModel { parameters ->
        AppProfileViewModel(
            uid = parameters[0],
            packageName = parameters[1],
            getAppGroup = get(),
            getProfile = get(),
            getDefaultUmountModules = get(),
            setProfile = get(),
            getSepolicy = get(),
            setSepolicy = get(),
            controlApp = get(),
            validateSepolicy = get(),
        )
    }
    viewModelOf(::HomeViewModel)
    viewModelOf(::InstallViewModel)
    viewModelOf(::MainIntentViewModel)
    viewModelOf(::KernelFlashViewModel)
    viewModel {
        SettingsViewModel(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    }
    viewModelOf(::ModuleViewModel)
    viewModelOf(::SuperUserViewModel)
    viewModelOf(::SuSFSViewModel)
    viewModelOf(::IoSchedulerViewModel)
    viewModelOf(::NetIsolateViewModel)
    viewModelOf(::ModuleRepoViewModel)
    viewModel { parameters -> ModuleDetailViewModel(parameters[0], get()) }
    viewModelOf(::TemplateViewModel)
    viewModel { parameters ->
        TemplateEditorViewModel(
            templateId = parameters[0],
            readOnly = parameters[1],
            isCreation = parameters[2],
            getTemplate = get(),
            saveTemplate = get(),
            deleteTemplate = get(),
        )
    }
    viewModelOf(::SulogViewModel)
    viewModelOf(::DynamicManagerViewModel)
    viewModelOf(::FlashViewModel)
    viewModelOf(::UmountManagerScreenViewModel)
    viewModel { parameters ->
        ExecuteModuleActionViewModel(
            moduleId = parameters[0],
            executeModuleAction = get(),
            saveModuleActionLog = get(),
        )
    }
}

val appModules = listOf(coreModule, repositoryModule, useCaseModule, viewModelModule)
