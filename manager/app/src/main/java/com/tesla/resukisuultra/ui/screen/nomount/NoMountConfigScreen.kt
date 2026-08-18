package com.tesla.resukisuultra.ui.screen.nomount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tesla.resukisuultra.R
import com.tesla.resukisuultra.data.nomount.NoMountModule
import com.tesla.resukisuultra.data.nomount.NoMountRule
import com.tesla.resukisuultra.ui.component.ConfirmResult
import com.tesla.resukisuultra.ui.component.PackageIcon
import com.tesla.resukisuultra.ui.component.rememberConfirmDialog
import com.tesla.resukisuultra.ui.component.settings.AppBackButton
import com.tesla.resukisuultra.ui.component.settings.SegmentedColumn
import com.tesla.resukisuultra.ui.component.settings.SettingsBaseWidget
import com.tesla.resukisuultra.ui.component.settings.SettingsTextFieldWidget
import com.tesla.resukisuultra.ui.component.settings.lazySegmentColumn
import com.tesla.resukisuultra.ui.navigation.LocalNavigator
import com.tesla.resukisuultra.ui.theme.CardConfig
import com.tesla.resukisuultra.ui.theme.ThemeConfig
import com.tesla.resukisuultra.ui.theme.blurEffect
import com.tesla.resukisuultra.ui.theme.renderBackgroundBlur
import com.tesla.resukisuultra.ui.viewmodel.NoMountUiAction
import com.tesla.resukisuultra.ui.viewmodel.NoMountUiState
import com.tesla.resukisuultra.ui.viewmodel.NoMountViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoMountConfigScreen() {
    val navigator = LocalNavigator.current
    val viewModel = koinViewModel<NoMountViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var showExclusionDialog by remember { mutableStateOf(false) }
    var showExclusionPicker by remember { mutableStateOf(false) }
    val confirmDialog = rememberConfirmDialog()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })

    // 仿 SUSFS: 进入页面/切 tab 才读取数据 (懒加载, 避免卡顿)
    LaunchedEffect(pagerState.currentPage) {
        viewModel.loadTab(pagerState.currentPage)
    }

    val removeConfirmTitle = stringResource(R.string.nomount_remove_confirm_title)
    val removeConfirmMessage = stringResource(R.string.nomount_remove_confirm_message)
    val clearConfirmTitle = stringResource(R.string.nomount_clear_confirm_title)
    val clearConfirmMessage = stringResource(R.string.nomount_clear_confirm_message)
    val confirmText = stringResource(R.string.confirm)
    val moduleActionTitle = stringResource(R.string.nomount_module_action_title)
    val moduleLoadConfirm = stringResource(R.string.nomount_module_load_confirm)
    val moduleUnloadConfirm = stringResource(R.string.nomount_module_unload_confirm)

    fun confirmThen(title: String, message: String, onConfirmed: () -> Unit) {
        scope.launch {
            if (confirmDialog.awaitConfirm(title = title, content = message, confirm = confirmText) == ConfirmResult.Confirmed) {
                onConfirmed()
            }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.blurEffect()) {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.nomount_title)) },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        AppBackButton(onClick = { navigator.pop() })
                    },
                    colors = TopAppBarDefaults.topAppBarColors().copy(
                        containerColor =
                            if (themeConfig.isEnableBlur)
                                Color.Transparent
                            else
                                MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                        scrolledContainerColor =
                            if (themeConfig.isEnableBlur)
                                Color.Transparent
                            else
                                MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                    ),
                    windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                )

                PrimaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor =
                        if (themeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                    edgePadding = 0.dp,
                    minTabWidth = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        modifier = Modifier.widthIn(min = TabRowDefaults.ScrollableTabRowMinTabWidth),
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = { Text(stringResource(R.string.nomount_tab_modules)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        modifier = Modifier.widthIn(min = TabRowDefaults.ScrollableTabRowMinTabWidth),
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = { Text(stringResource(R.string.nomount_tab_custom)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 2,
                        onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                        modifier = Modifier.widthIn(min = TabRowDefaults.ScrollableTabRowMinTabWidth),
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = { Text(stringResource(R.string.nomount_tab_exclusions)) },
                    )
                }
            }
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> NoMountModulesTab(
                        uiState = uiState,
                        innerPadding = innerPadding,
                        onModuleClick = {},
                        onLoadModule = { module ->
                            viewModel.dispatch(NoMountUiAction.LoadModule(module.id))
                        },
                        onUnloadModule = { module ->
                            viewModel.dispatch(NoMountUiAction.UnloadModule(module.id))
                        },
                    )

                    1 -> NoMountCustomTab(
                        uiState = uiState,
                        innerPadding = innerPadding,
                        onAddClick = { showAddDialog = true },
                        onRemoveRule = { rule ->
                            confirmThen(removeConfirmTitle, removeConfirmMessage.format(rule.virtual)) {
                                viewModel.dispatch(NoMountUiAction.RemoveRule(rule.virtual))
                            }
                        },
                        onClearCustom = {
                            confirmThen(clearConfirmTitle, clearConfirmMessage) {
                                viewModel.dispatch(NoMountUiAction.ClearRules)
                            }
                        },
                    )

                    2 -> NoMountExclusionsTab(
                        uiState = uiState,
                        innerPadding = innerPadding,
                        onAddClick = { showExclusionPicker = true },
                        onRemoveExclusion = { uid ->
                            viewModel.dispatch(NoMountUiAction.RemoveExclusion(uid))
                        },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        NoMountAddRuleDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { virtual, real ->
                showAddDialog = false
                viewModel.dispatch(NoMountUiAction.AddRule(virtual, real))
            },
        )
    }

    if (showExclusionPicker) {
        NoMountExclusionPickerSheet(
            excludedUids = uiState.exclusions.toSet(),
            onToggle = { uid ->
                if (uid in uiState.exclusions) {
                    viewModel.dispatch(NoMountUiAction.RemoveExclusion(uid))
                } else {
                    viewModel.dispatch(NoMountUiAction.AddExclusion(uid))
                }
            },
            onDismiss = { showExclusionPicker = false },
        )
    }
}

@Composable
private fun NoMountModulesTab(
    uiState: NoMountUiState,
    innerPadding: PaddingValues,
    onModuleClick: (NoMountModule) -> Unit,
    onLoadModule: (NoMountModule) -> Unit,
    onUnloadModule: (NoMountModule) -> Unit,
) {
    val statusSupportedTitle = stringResource(R.string.nomount_status_supported)
    val modulesTitle = stringResource(R.string.nomount_module_rules_title)
    val modulesEmpty = stringResource(R.string.nomount_modules_empty)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp,
            bottom = 16.dp,
        ),
    ) {
        item {
            SegmentedColumn {
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Info,
                        title = statusSupportedTitle,
                        description = stringResource(
                            if (uiState.supported) R.string.nomount_status_supported_yes
                            else R.string.nomount_status_supported_no
                        ),
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
        }
        if (uiState.modules.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = modulesEmpty,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item {
                Text(
                    text = modulesTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                )
            }
            lazySegmentColumn(
                items = uiState.modules,
                key = { _, m -> m.id },
            ) { _, module ->
                ModuleCard(
                    module = module,
                    onLoad = { onLoadModule(module) },
                    onUnload = { onUnloadModule(module) },
                )
            }
        }
    }
}

@Composable
private fun ModuleCard(
    module: NoMountModule,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val loadedText = stringResource(R.string.nomount_module_status_loaded)
    val disabledText = stringResource(R.string.nomount_module_status_disabled)
    val inactiveText = stringResource(R.string.nomount_module_status_inactive)
    val loadedDisabledText = stringResource(R.string.nomount_module_status_loaded_disabled)
    val hotLoadText = stringResource(R.string.nomount_hot_load)
    val hotUnloadText = stringResource(R.string.nomount_hot_unload)
    val notEnabledText = stringResource(R.string.nomount_module_not_enabled)
    val moduleSummary = stringResource(R.string.nomount_module_summary, module.fileCount, module.loaded)

    // 状态 (仿 web 界面): loaded>0 已注入 / disabled 禁用 / 否则未注入
    val statusText = when {
        module.loaded > 0 && !module.disabled -> loadedText
        module.loaded > 0 && module.disabled -> loadedDisabledText
        module.disabled -> disabledText
        else -> inactiveText
    }
    val statusColor = when {
        module.loaded > 0 && !module.disabled -> MaterialTheme.colorScheme.primary
        module.disabled -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // 按钮三态: 已注入→热卸载 / 禁用→未启用(不可点) / 其他→热加载
    val buttonText = when {
        module.loaded > 0 -> hotUnloadText
        module.disabled -> notEnabledText
        else -> hotLoadText
    }
    val buttonEnabled = module.loaded > 0 || !module.disabled
    val buttonAction = if (module.loaded > 0) onUnload else onLoad
    val buttonColor = when {
        module.loaded > 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    // 完整仿 ModuleItem (模块管理页卡片): Surface + blur + 名称/状态行 + 操作
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .renderBackgroundBlur(),
        color =
            if (themeConfig.isEnableBlurExp)
                Color.Transparent
            else
                MaterialTheme.colorScheme.surfaceBright.copy(cardConfig.cardAlpha),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(22.dp, 18.dp, 22.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.name,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                    fontFamily = MaterialTheme.typography.titleMedium.fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                    fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                )
                Text(
                    text = moduleSummary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                    fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                enabled = buttonEnabled,
                onClick = buttonAction,
            ) {
                Text(
                    text = buttonText,
                    color = if (buttonEnabled) buttonColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NoMountCustomTab(
    uiState: NoMountUiState,
    innerPadding: PaddingValues,
    onAddClick: () -> Unit,
    onRemoveRule: (NoMountRule) -> Unit,
    onClearCustom: () -> Unit,
) {
    val addRuleTitle = stringResource(R.string.nomount_add_rule)
    val addRuleSummary = stringResource(R.string.nomount_add_rule_summary)
    val clearAllTitle = stringResource(R.string.nomount_clear_all)
    val clearAllSummary = stringResource(R.string.nomount_clear_all_summary)
    val customRulesTitle = stringResource(R.string.nomount_custom_rules_title)
    val rulesEmpty = stringResource(R.string.nomount_rules_empty)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp,
            bottom = 16.dp,
        ),
    ) {
        item {
            SegmentedColumn {
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Add,
                        title = addRuleTitle,
                        description = addRuleSummary,
                        onClick = { onAddClick() },
                    )
                }
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Delete,
                        title = clearAllTitle,
                        description = clearAllSummary,
                        onClick = { onClearCustom() },
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
        }
        if (uiState.customRules.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = rulesEmpty,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item {
                Text(
                    text = customRulesTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                )
            }
            lazySegmentColumn(
                items = uiState.customRules,
                key = { _, rule -> "custom:${rule.virtual}" },
            ) { _, rule ->
                SettingsBaseWidget(
                    iconPlaceholder = false,
                    title = rule.virtual,
                    description = "→ ${rule.real}",
                    onClick = { onRemoveRule(rule) },
                )
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NoMountAddRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    val virtualState = rememberTextFieldState()
    val realState = rememberTextFieldState()
    val virtualText = virtualState.text.toString()
    val realText = realState.text.toString()
    val isValid = virtualText.isNotBlank() && realText.isNotBlank()
    val addRuleTitle = stringResource(R.string.nomount_add_rule)
    val virtualTitle = stringResource(R.string.nomount_rule_virtual)
    val realTitle = stringResource(R.string.nomount_rule_real)
    val saveText = stringResource(R.string.save)
    val cancelText = stringResource(R.string.cancel)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(addRuleTitle) },
        text = {
            SegmentedColumn {
                item {
                    SettingsTextFieldWidget(
                        modifier = Modifier.fillMaxWidth(),
                        renderBackgroundBlur = false,
                        state = virtualState,
                        title = virtualTitle,
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                }
                item {
                    SettingsTextFieldWidget(
                        modifier = Modifier.fillMaxWidth(),
                        renderBackgroundBlur = false,
                        state = realState,
                        title = realTitle,
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    onConfirm(virtualText.trim(), realText.trim())
                },
            ) {
                Text(saveText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelText)
            }
        },
    )
}

@Composable
private fun NoMountExclusionsTab(
    uiState: NoMountUiState,
    innerPadding: PaddingValues,
    onAddClick: () -> Unit,
    onRemoveExclusion: (Long) -> Unit,
) {
    val context = LocalContext.current
    val exclusionsTitle = stringResource(R.string.nomount_exclusions_title)
    val exclusionsAddTitle = stringResource(R.string.nomount_exclusions_add)
    val exclusionsEmpty = stringResource(R.string.nomount_exclusions_empty)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp,
            bottom = 16.dp,
        ),
    ) {
        item {
            // 标题行 + 添加按钮 (仿 NetIsolate)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = exclusionsTitle,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onAddClick() }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(exclusionsAddTitle)
                }
            }
        }

        if (uiState.exclusions.isEmpty()) {
            item {
                Text(
                    text = exclusionsEmpty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            items(uiState.exclusions) { uid ->
                val pkgs = remember(uid) {
                    runCatching { context.packageManager.getPackagesForUid(uid.toInt()) }.getOrNull()
                }
                val pkgName = pkgs?.firstOrNull()
                val label = remember(pkgName) {
                    pkgName?.let {
                        runCatching {
                            context.packageManager.getApplicationInfo(it, 0)
                                .loadLabel(context.packageManager).toString()
                        }.getOrNull()
                    } ?: "UID $uid"
                }

                // 已排除应用卡片 (仿 NetIsolate: 圆角 + 图标 + 垃圾桶)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PackageIcon(
                        packageName = pkgName ?: "",
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = pkgName ?: "UID $uid",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "UID $uid",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { onRemoveExclusion(uid) }) {
                        Icon(
                            imageVector = Icons.TwoTone.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoMountExclusionPickerSheet(
    excludedUids: Set<Long>,
    onToggle: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val cardConfig: CardConfig = koinInject()
    val pm = context.packageManager
    var searchQuery by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    val allApps = remember {
        pm.getInstalledApplications(0)
            .map { appInfo ->
                ExclusionAppEntry(
                    uid = appInfo.uid.toLong(),
                    packageName = appInfo.packageName,
                    label = appInfo.loadLabel(pm).toString(),
                    isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
                        (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                )
            }
            .distinctBy { it.uid }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    val filteredApps = remember(searchQuery, showSystem) {
        allApps
            .filter { showSystem || !it.isSystemApp }
            .let { apps ->
                if (searchQuery.isBlank()) apps
                else apps.filter {
                    it.packageName.contains(searchQuery, true) ||
                        it.label.contains(searchQuery, true) ||
                        it.uid.toString().contains(searchQuery, true)
                }
            }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f),
        ) {
            // 标题行: 选择应用 + 确定 (仿 NetIsolate)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.nomount_exclusions_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.confirm))
                }
            }

            Spacer(Modifier.height(8.dp))

            // 搜索框 (统一模块管理页样式: 圆形 + surfaceContainerHighest + 无指示线)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = cardConfig.cardAlpha),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = cardConfig.cardAlpha),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
            }

            // 显示系统应用
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = showSystem, onCheckedChange = { showSystem = it })
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.show_system_apps),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // 应用列表 (复选框 + 图标 + 分隔线, 仿 NetIsolate)
            val dividerColor = MaterialTheme.colorScheme.outlineVariant
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(filteredApps, key = { it.uid }) { app ->
                    val isExcluded = app.uid in excludedUids
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(app.uid) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .drawBehind {
                                drawLine(
                                    dividerColor,
                                    Offset(0f, size.height),
                                    Offset(size.width, size.height),
                                    strokeWidth = 0.5.dp.toPx(),
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = isExcluded,
                            onCheckedChange = { onToggle(app.uid) },
                        )
                        Spacer(Modifier.width(8.dp))
                        PackageIcon(
                            packageName = app.packageName,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ExclusionAppEntry(
    val uid: Long,
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
)

private fun String.format(vararg args: Any): String = java.lang.String.format(this, *args)
