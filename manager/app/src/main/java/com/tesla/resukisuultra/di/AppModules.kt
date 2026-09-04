package com.tesla.resukisuultra.di

import coil.ImageLoader
import com.tesla.resukisuultra.BuildConfig
import com.tesla.resukisuultra.data.AppSettingsRepository
import com.tesla.resukisuultra.data.application.ApplicationControlRepository
import com.tesla.resukisuultra.data.application.DynamicManagerRepository
import com.tesla.resukisuultra.data.download.DownloadRepository
import com.tesla.resukisuultra.data.file.ModuleFileRepository
import com.tesla.resukisuultra.data.flash.FlashRepository
import com.tesla.resukisuultra.data.kernel.KernelRepository
import com.tesla.resukisuultra.data.kernel.UmountRepository
import com.tesla.resukisuultra.data.logging.BugreportRepository
import com.tesla.resukisuultra.data.logging.SulogRepository
import com.tesla.resukisuultra.data.module.ModuleActionRepository
import com.tesla.resukisuultra.data.module.ModuleCatalogRepository
import com.tesla.resukisuultra.data.module.ModulePreferencesRepository
import com.tesla.resukisuultra.data.module.ModuleRepository
import com.tesla.resukisuultra.data.network.NetworkRequestRepository
import com.tesla.resukisuultra.data.network.NetworkStatusRepository
import com.tesla.resukisuultra.data.network.WebResourceRepository
import com.tesla.resukisuultra.data.packageinfo.AppIconDataSource
import com.tesla.resukisuultra.data.packageinfo.InstalledPackageCache
import com.tesla.resukisuultra.data.packageinfo.InstalledPackageRepository
import com.tesla.resukisuultra.data.packageinfo.RootServiceRepository
import com.tesla.resukisuultra.data.packageinfo.SuperUserRepository
import com.tesla.resukisuultra.data.profile.ProfileRepository
import com.tesla.resukisuultra.data.profile.ProfileTemplateRepository
import com.tesla.resukisuultra.data.settings.LocaleHelper
import com.tesla.resukisuultra.data.settings.LocaleRepository
import com.tesla.resukisuultra.data.settings.SettingsPlatformRepository
import com.tesla.resukisuultra.data.netisolate.NetIsolateRepository
import com.tesla.resukisuultra.data.shell.KsuCliRepository
import com.tesla.resukisuultra.data.shell.ShortcutRepository
import com.tesla.resukisuultra.data.startup.ApplicationInitializationRepository
import com.tesla.resukisuultra.data.startup.StartupRepository
import com.tesla.resukisuultra.data.susfs.SuSFSConfigHelper
import com.tesla.resukisuultra.data.susfs.SuSFSRepository
import com.tesla.resukisuultra.data.system.HomeRuntimeRepository
import com.tesla.resukisuultra.data.system.HomeStateRepository
import com.tesla.resukisuultra.data.text.HanziToPinyin
import com.tesla.resukisuultra.data.theme.MonetCompatColorSource
import com.tesla.resukisuultra.data.theme.ThemeRepository
import com.tesla.resukisuultra.data.update.ManagerUpdateRepository
import com.tesla.resukisuultra.data.webui.WebUiRepository
import com.tesla.resukisuultra.domain.text.TextTransliterator
import com.tesla.resukisuultra.domain.usecase.AddUmountPathUseCase
import com.tesla.resukisuultra.domain.usecase.ApplyLanguageUseCase
import com.tesla.resukisuultra.domain.usecase.BackupAllowlistUseCase
import com.tesla.resukisuultra.domain.usecase.CalculateInstalledModuleSizeUseCase
import com.tesla.resukisuultra.domain.usecase.CheckFlashModuleMountUseCase
import com.tesla.resukisuultra.domain.usecase.CheckManagerUpdateUseCase
import com.tesla.resukisuultra.domain.usecase.CleanSulogUseCase
import com.tesla.resukisuultra.domain.usecase.ClearDynamicManagerUseCase
import com.tesla.resukisuultra.domain.usecase.ConfigureSuLogUseCase
import com.tesla.resukisuultra.domain.usecase.ControlAppUseCase
import com.tesla.resukisuultra.domain.usecase.DeleteProfileTemplateUseCase
import com.tesla.resukisuultra.domain.usecase.EnableSulogUseCase
import com.tesla.resukisuultra.domain.usecase.EnqueueDownloadUseCase
import com.tesla.resukisuultra.domain.usecase.EnqueueManagerUpdateUseCase
import com.tesla.resukisuultra.domain.usecase.EnsureManagerInstalledUseCase
import com.tesla.resukisuultra.domain.usecase.ExecuteFlashOperationUseCase
import com.tesla.resukisuultra.domain.usecase.ExecuteModuleActionUseCase
import com.tesla.resukisuultra.domain.usecase.ExportProfileTemplatesUseCase
import com.tesla.resukisuultra.domain.usecase.ExtractModuleIdUseCase
import com.tesla.resukisuultra.domain.usecase.ExtractModuleNameUseCase
import com.tesla.resukisuultra.domain.usecase.FetchRemoteTextUseCase
import com.tesla.resukisuultra.domain.usecase.GenerateBugreportUseCase
import com.tesla.resukisuultra.domain.usecase.GetAppProfileUseCase
import com.tesla.resukisuultra.domain.usecase.GetAppSepolicyUseCase
import com.tesla.resukisuultra.domain.usecase.GetBooleanPreferenceUseCase
import com.tesla.resukisuultra.domain.usecase.GetCatalogModuleUseCase
import com.tesla.resukisuultra.domain.usecase.GetDefaultUmountModulesUseCase
import com.tesla.resukisuultra.domain.usecase.GetHomeBasicInfoUseCase
import com.tesla.resukisuultra.domain.usecase.GetHomeModuleOverviewUseCase
import com.tesla.resukisuultra.domain.usecase.GetHomeSuperuserCountUseCase
import com.tesla.resukisuultra.domain.usecase.GetInstallEnvironmentUseCase
import com.tesla.resukisuultra.domain.usecase.GetKernelFeatureSettingsUseCase
import com.tesla.resukisuultra.domain.usecase.GetKernelStatusUseCase
import com.tesla.resukisuultra.domain.usecase.GetManagerRuntimeInfoUseCase
import com.tesla.resukisuultra.domain.usecase.GetPlatformFeatureStatusUseCase
import com.tesla.resukisuultra.domain.usecase.GetProfileTemplateUseCase
import com.tesla.resukisuultra.domain.usecase.GetStringPreferenceUseCase
import com.tesla.resukisuultra.domain.usecase.GetStringSetPreferenceUseCase
import com.tesla.resukisuultra.domain.usecase.GetSuSFSStatusUseCase
import com.tesla.resukisuultra.domain.usecase.GetSuperUserAppGroupUseCase
import com.tesla.resukisuultra.domain.usecase.ImportAllowlistUseCase
import com.tesla.resukisuultra.domain.usecase.ImportProfileTemplatesUseCase
import com.tesla.resukisuultra.domain.usecase.InitializeApplicationUseCase
import com.tesla.resukisuultra.domain.usecase.IsLateLoadModeUseCase
import com.tesla.resukisuultra.domain.usecase.IsModuleUriAccessibleUseCase
import com.tesla.resukisuultra.domain.usecase.IsNetworkAvailableUseCase
import com.tesla.resukisuultra.domain.usecase.IsSystemLanguageSettingsUseCase
import com.tesla.resukisuultra.domain.usecase.LaunchSystemLanguageSettingsUseCase
import com.tesla.resukisuultra.domain.usecase.LoadSettingsPlatformUseCase
import com.tesla.resukisuultra.domain.usecase.ObserveCatalogModulesUseCase
import com.tesla.resukisuultra.domain.usecase.ObserveDownloadUseCase
import com.tesla.resukisuultra.domain.usecase.ObserveDynamicManagerStateUseCase
import com.tesla.resukisuultra.domain.usecase.ObserveInstalledModulesUseCase
import com.tesla.resukisuultra.domain.usecase.ObserveKernelFlashUseCase
import com.tesla.resukisuultra.domain.usecase.ObserveModuleCatalogOfflineUseCase
import com.tesla.resukisuultra.domain.usecase.ObserveModuleCatalogRefreshingUseCase
import com.tesla.resukisuultra.domain.usecase.ObserveProfileTemplateOfflineUseCase
import com.tesla.resukisuultra.domain.usecase.ObserveProfileTemplateRefreshingUseCase
import com.tesla.resukisuultra.domain.usecase.ObserveProfileTemplatesUseCase
import com.tesla.resukisuultra.domain.usecase.ObserveStartupStateUseCase
import com.tesla.resukisuultra.domain.usecase.ObserveSulogStateUseCase
import com.tesla.resukisuultra.domain.usecase.ObserveSuperUserStateUseCase
import com.tesla.resukisuultra.domain.usecase.ObserveUmountStateUseCase
import com.tesla.resukisuultra.domain.usecase.RebootUseCase
import com.tesla.resukisuultra.domain.usecase.RefreshDynamicManagerUseCase
import com.tesla.resukisuultra.domain.usecase.RefreshInstalledModulesUseCase
import com.tesla.resukisuultra.domain.usecase.RefreshModuleCatalogUseCase
import com.tesla.resukisuultra.domain.usecase.RefreshProfileTemplatesUseCase
import com.tesla.resukisuultra.domain.usecase.RefreshSulogUseCase
import com.tesla.resukisuultra.domain.usecase.RefreshSuperUsersUseCase
import com.tesla.resukisuultra.domain.usecase.RefreshUmountPathsUseCase
import com.tesla.resukisuultra.domain.usecase.RemovePreferenceUseCase
import com.tesla.resukisuultra.domain.usecase.RemoveUmountPathUseCase
import com.tesla.resukisuultra.domain.usecase.SaveModuleActionLogUseCase
import com.tesla.resukisuultra.domain.usecase.SaveProfileTemplateUseCase
import com.tesla.resukisuultra.domain.usecase.SelectDynamicManagerUseCase
import com.tesla.resukisuultra.domain.usecase.SetAppProfileUseCase
import com.tesla.resukisuultra.domain.usecase.SetAppSepolicyUseCase
import com.tesla.resukisuultra.domain.usecase.SetBooleanPreferenceUseCase
import com.tesla.resukisuultra.domain.usecase.SetDefaultUmountModulesUseCase
import com.tesla.resukisuultra.domain.usecase.SetKernelUmountEnabledUseCase
import com.tesla.resukisuultra.domain.usecase.SetManualDynamicManagerUseCase
import com.tesla.resukisuultra.domain.usecase.SetModuleEnabledUseCase
import com.tesla.resukisuultra.domain.usecase.SetModuleRemovedUseCase
import com.tesla.resukisuultra.domain.usecase.SetSelinuxHideEnabledUseCase
import com.tesla.resukisuultra.domain.usecase.SetStringPreferenceUseCase
import com.tesla.resukisuultra.domain.usecase.SetStringSetPreferenceUseCase
import com.tesla.resukisuultra.domain.usecase.SetSuEnabledUseCase
import com.tesla.resukisuultra.domain.usecase.SetWebViewZygoteUmountEnabledUseCase
import com.tesla.resukisuultra.domain.usecase.StartKernelFlashUseCase
import com.tesla.resukisuultra.domain.usecase.SuSFSConfigUseCase
import com.tesla.resukisuultra.domain.usecase.TakeModuleUriPermissionUseCase
import com.tesla.resukisuultra.domain.usecase.TransliterateTextUseCase
import com.tesla.resukisuultra.domain.usecase.UpdateAppearanceUseCase
import com.tesla.resukisuultra.domain.usecase.UpdateCachedModuleEnabledUseCase
import com.tesla.resukisuultra.domain.usecase.UpdatePlatformSettingUseCase
import com.tesla.resukisuultra.domain.usecase.ValidateSepolicyUseCase
import com.tesla.resukisuultra.ui.activity.util.ThemeUtils
import com.tesla.resukisuultra.ui.component.ZipFileDetector
import com.tesla.resukisuultra.ui.theme.BackgroundManager
import com.tesla.resukisuultra.ui.theme.CardConfig
import com.tesla.resukisuultra.ui.theme.ThemeConfig
import com.tesla.resukisuultra.ui.util.module.Shortcut
import com.tesla.resukisuultra.ui.viewmodel.AppProfileViewModel
import com.tesla.resukisuultra.ui.viewmodel.DynamicManagerViewModel
import com.tesla.resukisuultra.ui.viewmodel.ExecuteModuleActionViewModel
import com.tesla.resukisuultra.ui.viewmodel.FlashViewModel
import com.tesla.resukisuultra.ui.viewmodel.HomeViewModel
import com.tesla.resukisuultra.ui.viewmodel.IoSchedulerViewModel
import com.tesla.resukisuultra.ui.viewmodel.InstallViewModel
import com.tesla.resukisuultra.ui.viewmodel.KernelFlashViewModel
import com.tesla.resukisuultra.ui.viewmodel.MainIntentViewModel
import com.tesla.resukisuultra.ui.viewmodel.ModuleDetailViewModel
import com.tesla.resukisuultra.ui.viewmodel.ModuleRepoViewModel
import com.tesla.resukisuultra.ui.viewmodel.ModuleViewModel
import com.tesla.resukisuultra.ui.viewmodel.SettingsViewModel
import com.tesla.resukisuultra.ui.viewmodel.SuSFSViewModel
import com.tesla.resukisuultra.ui.viewmodel.NetIsolateViewModel
import com.tesla.resukisuultra.ui.viewmodel.SulogViewModel
import com.tesla.resukisuultra.ui.viewmodel.SuperUserViewModel
import com.tesla.resukisuultra.ui.viewmodel.TemplateEditorViewModel
import com.tesla.resukisuultra.ui.viewmodel.TemplateViewModel
import com.tesla.resukisuultra.ui.viewmodel.UmountManagerScreenViewModel
import com.tesla.resukisuultra.ui.webui.MonetColorsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

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
                        .header("User-Agent", "ReSukiSU/${BuildConfig.VERSION_CODE}")
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
    factoryOf(::GetHomeModuleOverviewUseCase)
    factoryOf(::GetHomeSuperuserCountUseCase)
    factoryOf(::IsNetworkAvailableUseCase)
    factoryOf(::LoadSettingsPlatformUseCase)
    factoryOf(::UpdateAppearanceUseCase)
    factoryOf(::UpdatePlatformSettingUseCase)
    factoryOf(::GetPlatformFeatureStatusUseCase)
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
    factoryOf(::SetWebViewZygoteUmountEnabledUseCase)
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
    viewModelOf(::SettingsViewModel)
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
