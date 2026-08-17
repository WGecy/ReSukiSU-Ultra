package com.tesla.resukisuultra.ui.screen.main

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.automirrored.twotone.Undo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Adb
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.DeleteForever
import androidx.compose.material.icons.twotone.ElectricalServices
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.FolderDelete
import androidx.compose.material.icons.twotone.FolderOff
import androidx.compose.material.icons.twotone.GppGood
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.DataUsage
import androidx.compose.material.icons.twotone.Policy
import androidx.compose.material.icons.twotone.RadioButtonChecked
import androidx.compose.material.icons.twotone.RadioButtonUnchecked
import androidx.compose.material.icons.twotone.RemoveCircle
import androidx.compose.material.icons.twotone.RemoveModerator
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Share
import androidx.compose.material.icons.twotone.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.tesla.resukisuultra.ui.component.settings.AppBackButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tesla.resukisuultra.BuildConfig
import com.tesla.resukisuultra.data.fakelock.FakeLockRepository
import com.tesla.resukisuultra.data.shell.KsuCliRepository
import com.tesla.resukisuultra.domain.usecase.GenerateBugreportUseCase
import com.tesla.resukisuultra.R
import com.tesla.resukisuultra.ui.component.ConfirmResult
import com.tesla.resukisuultra.ui.component.DialogHandle
import com.tesla.resukisuultra.ui.component.rememberConfirmDialog
import com.tesla.resukisuultra.ui.component.rememberCustomDialog
import com.tesla.resukisuultra.ui.component.rememberLoadingDialog
import com.tesla.resukisuultra.ui.component.settings.SegmentedColumn
import com.tesla.resukisuultra.ui.component.settings.SettingsBaseWidget
import com.tesla.resukisuultra.ui.component.settings.SettingsChooseWidget
import com.tesla.resukisuultra.ui.component.settings.SettingsJumpPageWidget
import com.tesla.resukisuultra.ui.component.settings.SettingsSwitchWidget
import com.tesla.resukisuultra.ui.component.SwipeableSnackbarHost
import com.tesla.resukisuultra.ui.navigation.LocalNavigator
import com.tesla.resukisuultra.ui.navigation.Route
import com.tesla.resukisuultra.ui.theme.blurEffect
import com.tesla.resukisuultra.ui.theme.blurSource
import com.tesla.resukisuultra.ui.theme.CardConfig
import com.tesla.resukisuultra.ui.theme.ThemeConfig
import com.tesla.resukisuultra.ui.util.LocalSnackbarHost
import com.tesla.resukisuultra.ui.util.showReplacingSnackbar
import com.tesla.resukisuultra.ui.viewmodel.HomeViewModel
import com.tesla.resukisuultra.ui.viewmodel.SettingsUiAction
import com.tesla.resukisuultra.ui.viewmodel.SettingsViewModel
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsCoreScreen() {
    val navigator = LocalNavigator.current
    val settingsViewModel = koinViewModel<SettingsViewModel>()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackBarHost = SnackbarHostState()
    val loadingDialog = rememberLoadingDialog()
    val generateBugreport = koinInject<GenerateBugreportUseCase>()
    var showBottomsheet by remember { mutableStateOf(false) }
    val logSaved = stringResource(R.string.log_saved)
    val exportBugreportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            loadingDialog.show()
            context.contentResolver.openOutputStream(uri)?.use { output ->
                generateBugreport().inputStream().use { it.copyTo(output) }
            }
            loadingDialog.hide()
            snackBarHost.showReplacingSnackbar(logSaved)
        }
    }

    LaunchedEffect(Unit) {
        settingsViewModel.dispatch(SettingsUiAction.LoadFeatureSettings)
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.blurEffect()) {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.configuration)) },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        AppBackButton(
                            onClick = { navigator.pop() }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors().copy(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
        ) {
            item {
                val modeItems = listOf(
                    stringResource(id = R.string.settings_mode_default),
                    stringResource(id = R.string.settings_mode_disable_until_reboot),
                    stringResource(id = R.string.settings_mode_disable_always),
                )

                SegmentedColumn(
                        modifier = Modifier.fillMaxWidth(),
                        content = {
                            item {
                                // 配置文件模板入口
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.GppGood,
                                    title = stringResource(R.string.settings_profile_template),
                                    description = stringResource(R.string.settings_profile_template_summary),
                                    onClick = {
                                        navigator.push(Route.AppProfileTemplate)
                                    }
                                )
                            }

                            item {
                                val suSummary = when (uiState.suStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_sucompat_summary)
                                }
                                SettingsChooseWidget(
                                    icon = Icons.TwoTone.RemoveModerator,
                                    title = stringResource(id = R.string.settings_sucompat),
                                    description = suSummary,
                                    items = modeItems,
                                    enabled = uiState.suStatus == "supported",
                                    selectedIndex = uiState.suCompatMode,
                                    onSelectedIndexChange = { index ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetSuCompatMode(
                                                index
                                            )
                                        )
                                    },
                                )
                            }

                            item {
                                val umountSummary = when (uiState.kernelUmountStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_kernel_umount_summary)
                                }
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.RemoveCircle,
                                    title = stringResource(id = R.string.settings_kernel_umount),
                                    description = umountSummary,
                                    enabled = uiState.kernelUmountStatus == "supported",
                                    checked = uiState.isKernelUmountEnabled,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetKernelUmount(
                                                enabled
                                            )
                                        )
                                    },
                                )
                            }

                            item(
                                visible = homeState.systemStatus.isLateLoadMode
                            ) {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.ElectricalServices,
                                    title = stringResource(id = R.string.settings_auto_jailbreak),
                                    description = stringResource(id = R.string.settings_auto_jailbreak_summary),
                                    checked = uiState.autoJailbreakEnabled,
                                    onCheckedChange = { value ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetAutoJailbreak(
                                                value
                                            )
                                        )
                                    }
                                )
                            }

                            item(
                                visible = Build.VERSION.SDK_INT > Build.VERSION_CODES.Q
                            ) {
                                val adbRootSummary = when (uiState.adbRootStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_adb_root_summary)
                                }

                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Adb,
                                    title = stringResource(id = R.string.settings_adb_root),
                                    description = adbRootSummary,
                                    checked = uiState.isAdbRootEnabled,
                                    enabled = uiState.adbRootStatus == "supported",
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetAdbRoot(
                                                enabled
                                            )
                                        )
                                    },
                                )
                            }


                            item {
                                val sulogSummary = when (uiState.sulogStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_sulog_summary)
                                }
                                SettingsSwitchWidget(
                                    icon = Icons.AutoMirrored.TwoTone.Article,
                                    title = stringResource(id = R.string.settings_sulog),
                                    description = sulogSummary,
                                    enabled = uiState.sulogStatus == "supported",
                                    checked = uiState.isSuLogEnabled,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(SettingsUiAction.SetSuLog(enabled))
                                    },
                                )
                            }


                            item {
                                val selinuxHideSummary = when (uiState.selinuxHideStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_selinux_hide_summary)
                                }
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Policy,
                                    title = stringResource(id = R.string.settings_selinux_hide),
                                    description = selinuxHideSummary,
                                    enabled = uiState.selinuxHideStatus == "supported",
                                    checked = uiState.isSelinuxHideEnabled,
                                    onCheckedChange = { checked ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetSelinuxHide(
                                                checked
                                            )
                                        )
                                    },
                                )
                            }

                            item(
                                visible = runCatching {
                                    KsuCliRepository(context).exec(
                                        "/data/adb/ksu/bin/ksud feature check fusebpf"
                                    )?.contains("supported") == true
                                }.getOrDefault(false)
                            ) {
                                // FUSEBPF 直通修复开关 (ReSukiSU-Ultra)
                                val ksuCli = remember { KsuCliRepository(context) }
                                var fusebpfFixEnabled by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    // param_get_bool 输出 Y/N (内核 bool 参数惯例)
                                    val raw = ksuCli.exec(
                                        "cat /sys/module/kernelsu/parameters/fusebpf_fix"
                                    )?.trim().orEmpty()
                                    fusebpfFixEnabled = raw.equals("1", true) ||
                                        raw.equals("Y", true) || raw.equals("true", true)
                                }
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.DataUsage,
                                    title = stringResource(R.string.settings_fusebpf_fix),
                                    description = stringResource(R.string.settings_fusebpf_fix_summary),
                                    checked = fusebpfFixEnabled,
                                    onCheckedChange = { enabled ->
                                        fusebpfFixEnabled = enabled
                                        scope.launch {
                                            ksuCli.execKsud(
                                                "fusebpf ${if (enabled) "enable" else "disable"}",
                                                newShell = true
                                            )
                                        }
                                    },
                                )
                            }

                            item {
                                // 伪装 BL 锁状态开关
                                var fakeLockEnabled by remember { mutableStateOf(false) }
                                var fakeLockLoaded by remember { mutableStateOf(false) }
                                val fakeLockRepo = remember {
                                    FakeLockRepository(context, KsuCliRepository(context))
                                }
                                LaunchedEffect(Unit) {
                                    fakeLockEnabled = fakeLockRepo.isEnabled()
                                    fakeLockLoaded = true
                                }
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.GppGood,
                                    title = stringResource(id = R.string.settings_fake_lock),
                                    description = stringResource(id = R.string.settings_fake_lock_summary),
                                    checked = fakeLockEnabled,
                                    onCheckedChange = { checked ->
                                        fakeLockEnabled = checked
                                        scope.launch { fakeLockRepo.setEnabled(checked) }
                                    },
                                )
                            }

                            item {
                                // 卸载模块开关
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.FolderDelete,
                                    title = stringResource(id = R.string.settings_umount_modules_default),
                                    description = stringResource(id = R.string.settings_umount_modules_default_summary),
                                    checked = uiState.defaultUmountModules,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetDefaultUmountModules(
                                                enabled
                                            )
                                        )
                                    },
                                )
                            }
                        }
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsAppScreen() {
    val navigator = LocalNavigator.current
    val settingsViewModel = koinViewModel<SettingsViewModel>()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackBarHost = SnackbarHostState()
    val loadingDialog = rememberLoadingDialog()
    val generateBugreport = koinInject<GenerateBugreportUseCase>()
    var showBottomsheet by remember { mutableStateOf(false) }
    val logSaved = stringResource(R.string.log_saved)
    val exportBugreportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            loadingDialog.show()
            context.contentResolver.openOutputStream(uri)?.use { output ->
                generateBugreport().inputStream().use { it.copyTo(output) }
            }
            loadingDialog.hide()
            snackBarHost.showReplacingSnackbar(logSaved)
        }
    }

    LaunchedEffect(Unit) {
        settingsViewModel.dispatch(SettingsUiAction.LoadFeatureSettings)
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.blurEffect()) {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.app_settings)) },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        AppBackButton(
                            onClick = { navigator.pop() }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors().copy(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
        ) {
            item {
                    // 应用设置卡片
                    SegmentedColumn(
                        modifier = Modifier.fillMaxWidth(),
                        content = {
                            expandableItem(
                                expanded = uiState.checkManagerUpdate,
                                topContent = {
                                    SettingsSwitchWidget(
                                        icon = Icons.TwoTone.Update,
                                        title = stringResource(R.string.settings_check_manager_update),
                                        description = stringResource(R.string.settings_check_manager_update_summary),
                                        checked = uiState.checkManagerUpdate,
                                        onCheckedChange = { enabled ->
                                            settingsViewModel.dispatch(
                                                SettingsUiAction.SetManagerUpdateCheck(
                                                    enabled
                                                )
                                            )
                                        }
                                    )
                                }
                            ) {
                                item(
                                    topPadding = 1.dp
                                ) {
                                    SettingsSwitchWidget(
                                        title = stringResource(R.string.settings_check_beta_update),
                                        description = stringResource(R.string.settings_check_beta_update_summary),
                                        checked = uiState.checkBetaUpdate,
                                        onCheckedChange = { enabled ->
                                            settingsViewModel.dispatch(
                                                SettingsUiAction.SetBetaUpdateCheck(
                                                    enabled
                                                )
                                            )
                                        }
                                    )
                                }
                            }

                            item {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Extension,
                                    title = stringResource(R.string.settings_check_module_update),
                                    description = stringResource(R.string.settings_check_module_update_summary),
                                    checked = uiState.checkModuleUpdate,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetModuleUpdateCheck(
                                                enabled
                                            )
                                        )
                                    }
                                )
                            }

                            item {
                                // 更多设置
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Settings,
                                    title = stringResource(R.string.theme_settings),
                                    description = stringResource(R.string.theme_settings),
                                    onClick = {
                                        navigator.push(Route.ThemeSettings)
                                    }
                                )
                            }
                        }
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsToolsScreen() {
    val navigator = LocalNavigator.current
    val settingsViewModel = koinViewModel<SettingsViewModel>()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackBarHost = SnackbarHostState()
    val loadingDialog = rememberLoadingDialog()
    val generateBugreport = koinInject<GenerateBugreportUseCase>()
    var showBottomsheet by remember { mutableStateOf(false) }
    val logSaved = stringResource(R.string.log_saved)
    val exportBugreportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            loadingDialog.show()
            context.contentResolver.openOutputStream(uri)?.use { output ->
                generateBugreport().inputStream().use { it.copyTo(output) }
            }
            loadingDialog.hide()
            snackBarHost.showReplacingSnackbar(logSaved)
        }
    }

    LaunchedEffect(Unit) {
        settingsViewModel.dispatch(SettingsUiAction.LoadFeatureSettings)
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.blurEffect()) {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.tools)) },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        AppBackButton(
                            onClick = { navigator.pop() }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors().copy(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
        ) {
            item {
                    // 工具卡片
                    SegmentedColumn(
                        modifier = Modifier.fillMaxWidth(),
                        content = {
                            item {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.BugReport,
                                    title = stringResource(R.string.send_log),
                                    onClick = {
                                        showBottomsheet = true
                                    }
                                ) {}
                            }

                            if (homeState.systemStatus.isValid) {
                                item {
                                    SettingsJumpPageWidget(
                                        icon = Icons.TwoTone.Security,
                                        title = stringResource(R.string.dynamic_manager_title),
                                        description = stringResource(R.string.dynamic_manager_settings_summary),
                                        onClick = {
                                            navigator.push(Route.DynamicManager)
                                        }
                                    )
                                }

                                item(visible = uiState.isKernelUmountEnabled) {
                                    SettingsJumpPageWidget(
                                        icon = Icons.TwoTone.FolderOff,
                                        title = stringResource(R.string.umount_path_manager),
                                        description = stringResource(R.string.umount_path_manager_summary),
                                        onClick = {
                                            navigator.push(Route.UmountManager)
                                        }
                                    )
                                }
                            }

                            if (homeState.systemStatus.lkmMode == true) {
                                item {
                                    UninstallItem {
                                        loadingDialog.withLoading(it)
                                    }
                                }
                            }
                        }
                )
            }
        }
    }
}
