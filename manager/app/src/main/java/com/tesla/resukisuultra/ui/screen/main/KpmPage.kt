package com.tesla.resukisuultra.ui.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tesla.resukisuultra.R
import com.tesla.resukisuultra.ui.component.settings.SegmentedColumn
import com.tesla.resukisuultra.ui.component.settings.SettingsBaseWidget
import com.tesla.resukisuultra.ui.theme.blurEffect
import com.tesla.resukisuultra.ui.viewmodel.KpmViewModel
import org.koin.compose.viewmodel.koinViewModel

/** KPM 内核补丁模块页 (单独卡片, 仿模块管理) */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KpmPage(bottomPadding: androidx.compose.ui.unit.Dp) {
    val viewModel: KpmViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pageLoaded by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val path = runCatching {
                // 复制到缓存再加载 (照 SukiSU)
                val cache = java.io.File(context.cacheDir, "kpm_install.kpm")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cache.outputStream().use { input.copyTo(it) }
                }
                cache.absolutePath
            }.getOrNull()
            if (path != null) viewModel.install(path)
        }
    }

    LaunchedEffect(Unit) {
        pageLoaded = false
        viewModel.refresh()
        pageLoaded = true
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = {
                    Text(
                        text = stringResource(R.string.kpm_title),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = bottomPadding + 8.dp,
            ),
        ) {
            item {
                Spacer(Modifier.height(12.dp))
            }
            // 状态卡
            item {
                SegmentedColumn {
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Memory,
                            title = stringResource(R.string.kpm_title),
                            description = if (pageLoaded && uiState.loaded) {
                                stringResource(R.string.kpm_count, uiState.modules.size)
                            } else {
                                stringResource(R.string.iosched_no_data)
                            },
                        )
                    }
                    item {
                        // 安装 KPM 模块 (照 SukiSU: 文件选择 → ksud kpm load)
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Add,
                            title = stringResource(R.string.kpm_install),
                            description = stringResource(R.string.kpm_install_hint),
                            onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) },
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(20.dp))
            }
            // 模块列表 (骨架先占位)
            if (!pageLoaded || !uiState.loaded) {
                item {
                    Text(
                        text = stringResource(R.string.iosched_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }
            } else if (uiState.modules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.kpm_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    SegmentedColumn(title = stringResource(R.string.kpm_modules)) {
                        uiState.modules.forEach { mod ->
                            item {
                                SettingsBaseWidget(
                                    iconPlaceholder = false,
                                    title = mod.name,
                                    description = buildString {
                                        append(mod.version)
                                        if (mod.author.isNotBlank()) append(" · ").append(mod.author)
                                        if (mod.description.isNotBlank()) append("\n").append(mod.description)
                                    },
                                    trailingContent = {
                                        Icon(
                                            imageVector = Icons.TwoTone.Delete,
                                            contentDescription = stringResource(R.string.kpm_unload),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    onClick = { viewModel.unload(mod.name) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
