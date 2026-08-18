package com.tesla.resukisuultra.ui.screen.nomount

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tesla.resukisuultra.R
import com.tesla.resukisuultra.ui.component.ConfirmResult
import com.tesla.resukisuultra.ui.component.settings.AppBackButton
import com.tesla.resukisuultra.ui.component.DialogHandle
import com.tesla.resukisuultra.ui.component.rememberConfirmDialog
import com.tesla.resukisuultra.ui.component.settings.SegmentedColumn
import com.tesla.resukisuultra.ui.component.settings.SettingsBaseWidget
import com.tesla.resukisuultra.ui.component.settings.SettingsTextFieldWidget
import com.tesla.resukisuultra.ui.component.settings.lazySegmentColumn
import com.tesla.resukisuultra.ui.navigation.LocalNavigator
import com.tesla.resukisuultra.ui.viewmodel.NoMountUiAction
import com.tesla.resukisuultra.ui.viewmodel.NoMountViewModel
import com.tesla.resukisuultra.ui.theme.blurEffect
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoMountConfigScreen() {
    val navigator = LocalNavigator.current
    val viewModel = koinViewModel<NoMountViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    val confirmDialog = rememberConfirmDialog()

    val removeConfirmTitle = stringResource(R.string.nomount_remove_confirm_title)
    val removeConfirmMessage = stringResource(R.string.nomount_remove_confirm_message)
    val clearConfirmTitle = stringResource(R.string.nomount_clear_confirm_title)
    val clearConfirmMessage = stringResource(R.string.nomount_clear_confirm_message)
    val confirmText = stringResource(R.string.confirm)
    val cancelText = stringResource(R.string.cancel)

    fun confirmThen(confirmTitle: String, confirmMessage: String, onConfirmed: () -> Unit) {
        scope.launch {
            if (confirmDialog.awaitConfirm(title = confirmTitle, content = confirmMessage, confirm = confirmText) == ConfirmResult.Confirmed) {
                onConfirmed()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBackButton(onClick = { navigator.pop() })
                Text(
                    text = stringResource(R.string.nomount_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            PrimaryScrollableTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.nomount_tab_status)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.nomount_tab_rules)) },
                )
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            } else {
                when (selectedTab) {
                    0 -> NoMountStatusTab(uiState = uiState)
                    1 -> NoMountRulesTab(
                        uiState = uiState,
                        onAddClick = { showAddDialog = true },
                        onRemoveRule = { rule ->
                            confirmThen(removeConfirmTitle, removeConfirmMessage.format(rule.virtual)) {
                                viewModel.dispatch(NoMountUiAction.RemoveRule(rule.virtual))
                            }
                        },
                        onClearAll = {
                            confirmThen(clearConfirmTitle, clearConfirmMessage) {
                                viewModel.dispatch(NoMountUiAction.ClearRules)
                            }
                        },
                    )
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
}

@Composable
private fun NoMountStatusTab(
    uiState: com.tesla.resukisuultra.ui.viewmodel.NoMountUiState,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            SegmentedColumn {
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Info,
                        title = stringResource(R.string.nomount_status_supported),
                        description = stringResource(
                            if (uiState.supported) R.string.nomount_status_supported_yes
                            else R.string.nomount_status_supported_no
                        ),
                    )
                }
                if (uiState.supported) {
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Info,
                            title = stringResource(R.string.nomount_version),
                            description = uiState.version.ifBlank { "-" },
                        )
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
        }
        item {
            SegmentedColumn {
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Info,
                        title = stringResource(R.string.nomount_description),
                        description = stringResource(R.string.nomount_description_summary),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoMountRulesTab(
    uiState: com.tesla.resukisuultra.ui.viewmodel.NoMountUiState,
    onAddClick: () -> Unit,
    onRemoveRule: (com.tesla.resukisuultra.data.nomount.NoMountRule) -> Unit,
    onClearAll: () -> Unit,
) {
    val rulesTitle = stringResource(R.string.nomount_rules_title)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            SegmentedColumn {
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Add,
                        title = stringResource(R.string.nomount_add_rule),
                        description = stringResource(R.string.nomount_add_rule_summary),
                        onClick = { onAddClick() },
                    )
                }
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Delete,
                        title = stringResource(R.string.nomount_clear_all),
                        description = stringResource(R.string.nomount_clear_all_summary),
                        onClick = { onClearAll() },
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
        }
        if (uiState.rules.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.nomount_rules_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            lazySegmentColumn(
                title = rulesTitle,
                items = uiState.rules,
                key = { _, rule -> rule.virtual },
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nomount_add_rule)) },
        text = {
            SegmentedColumn {
                item {
                    SettingsTextFieldWidget(
                        modifier = Modifier.fillMaxWidth(),
                        renderBackgroundBlur = false,
                        state = virtualState,
                        title = stringResource(R.string.nomount_rule_virtual),
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                }
                item {
                    SettingsTextFieldWidget(
                        modifier = Modifier.fillMaxWidth(),
                        renderBackgroundBlur = false,
                        state = realState,
                        title = stringResource(R.string.nomount_rule_real),
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
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun String.format(vararg args: Any): String = java.lang.String.format(this, *args)
