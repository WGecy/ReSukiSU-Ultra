package com.tesla.resukisuultra.ui.screen.nomount

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tesla.resukisuultra.R
import com.tesla.resukisuultra.data.nomount.NoMountModule
import com.tesla.resukisuultra.data.nomount.NoMountRule
import com.tesla.resukisuultra.ui.component.ConfirmResult
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
    val confirmDialog = rememberConfirmDialog()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

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
                        onModuleClick = { module ->
                            confirmThen(
                                moduleActionTitle,
                                if (module.loaded > 0) {
                                    moduleUnloadConfirm.format(module.name)
                                } else {
                                    moduleLoadConfirm.format(module.name)
                                },
                            ) {
                                if (module.loaded > 0) {
                                    viewModel.dispatch(NoMountUiAction.UnloadModule(module.id))
                                } else {
                                    viewModel.dispatch(NoMountUiAction.LoadModule(module.id))
                                }
                            }
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
}

@Composable
private fun NoMountModulesTab(
    uiState: NoMountUiState,
    innerPadding: PaddingValues,
    onModuleClick: (NoMountModule) -> Unit,
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
                SettingsBaseWidget(
                    icon = Icons.TwoTone.Folder,
                    title = module.name,
                    description = stringResource(
                        R.string.nomount_module_summary,
                        module.fileCount,
                        module.loaded,
                    ),
                    onClick = { onModuleClick(module) },
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

private fun String.format(vararg args: Any): String = java.lang.String.format(this, *args)
