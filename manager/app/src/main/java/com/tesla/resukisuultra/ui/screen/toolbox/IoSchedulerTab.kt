package com.tesla.resukisuultra.ui.screen.toolbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tesla.resukisuultra.R
import com.tesla.resukisuultra.ui.component.settings.SegmentedColumn
import com.tesla.resukisuultra.ui.component.settings.SettingsBaseWidget
import com.tesla.resukisuultra.ui.viewmodel.IoSchedulerViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun IoSchedulerTab(
    innerPadding: PaddingValues,
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
) {
    val viewModel: IoSchedulerViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 仿 SUSFS configEnabledLoaded: 页面级加载态 — 先"无数据", 刷新真实完成后显示
    var pageLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        pageLoaded = false
        viewModel.refresh()
        pageLoaded = true
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        item {
            Spacer(Modifier.height(innerPadding.calculateTopPadding()))
        }

        // 状态卡: 当前调度器
        item {
            SegmentedColumn {
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Speed,
                        title = stringResource(R.string.iosched_current),
                        description = if (pageLoaded && uiState.loaded) {
                            if (uiState.current.isNotBlank()) uiState.current
                            else stringResource(R.string.iosched_none)
                        } else {
                            stringResource(R.string.iosched_no_data)
                        },
                    )
                }
            }
        }

        // 调度器列表 (点击切换)
        item {
            Spacer(Modifier.height(20.dp))
        }
        if (pageLoaded && uiState.loaded) {
            if (uiState.available.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.iosched_no_data),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    SegmentedColumn {
                        uiState.available.forEach { name ->
                            item {
                                val isCurrent = name == uiState.current
                                SettingsBaseWidget(
                                    iconPlaceholder = false,
                                    title = name,
                                    description = if (isCurrent) {
                                        stringResource(R.string.iosched_in_use)
                                    } else {
                                        stringResource(R.string.iosched_switch_hint)
                                    },
                                    onClick = {
                                        if (!isCurrent) {
                                            viewModel.setScheduler(name)
                                        }
                                    },
                                    trailingContent = {
                                        if (isCurrent) {
                                            androidx.compose.material3.Icon(
                                                imageVector = Icons.TwoTone.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    },
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
                                title = stringResource(R.string.iosched_title),
                                description = stringResource(R.string.iosched_summary),
                            )
                        }
                    }
                }
            }
        }
    }
}
