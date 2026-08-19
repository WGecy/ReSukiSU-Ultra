package com.tesla.resukisuultra.ui.screen.netisolate

import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import com.tesla.resukisuultra.ui.theme.ThemeConfig
import com.tesla.resukisuultra.ui.component.settings.AppBackButton
import com.tesla.resukisuultra.ui.component.PackageIcon
import com.tesla.resukisuultra.ui.component.settings.SegmentedColumn
import com.tesla.resukisuultra.ui.component.settings.SettingsBaseWidget
import com.tesla.resukisuultra.ui.component.settings.SettingsJumpPageWidget
import com.tesla.resukisuultra.ui.component.settings.lazySegmentColumn
import com.tesla.resukisuultra.ui.component.settings.SettingsSwitchWidget
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Inbox
import androidx.compose.material.icons.twotone.Info

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.twotone.WifiOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import com.tesla.resukisuultra.ui.component.settings.AppBackButton
import com.tesla.resukisuultra.ui.navigation.LocalNavigator
import com.tesla.resukisuultra.ui.theme.CardConfig
import org.koin.compose.viewmodel.koinViewModel

import com.tesla.resukisuultra.ui.viewmodel.NetIsolateUiState
import com.tesla.resukisuultra.ui.viewmodel.NetIsolateViewModel

import com.tesla.resukisuultra.ui.theme.blurEffect
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tesla.resukisuultra.R
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.tesla.resukisuultra.data.netisolate.NetIsolateRepository
import com.tesla.resukisuultra.data.shell.KsuCliRepository
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import org.koin.compose.koinInject
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class AppListEntry(
    val uid: Int,
    val packageName: String,
    val label: String,
    val packageInfo: PackageInfo?,
    val isSystemApp: Boolean,
)

/**
 * 联网隔离设置页 (ReSukiSU Ultra)
 * 内核态 UID 联网阻止: 开关 + 应用列表管理 (FolkPatch 风格底部弹窗)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetIsolateTab(
    innerPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    onAddClick: () -> Unit,
) {
    val context = LocalContext.current
    // 仿 SUSFS: state 提升到 ViewModel (组合销毁不丢, 进入秒开, 后台刷新不闪)
    val viewModel: NetIsolateViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 仿 SUSFS configEnabledLoaded: 页面级加载态 — 进入先关(灰), 刷新真实完成后才开
    var pageLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        pageLoaded = false
        viewModel.refresh()
        pageLoaded = true
    }
    val uidListTitle = stringResource(R.string.netisolate_uid_list)

    // 仿 SUSFS StatusTab: 无 contentPadding 左右 (settings 组件自带间距), Spacer 顶部
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        item {
            Spacer(Modifier.height(innerPadding.calculateTopPadding()))
        }

        // 开关卡片 (首帧不组合 — 刷新完成后出现, 进入零组合开销)
        if (pageLoaded && uiState.loaded) {
            item {
                SegmentedColumn {
                    item {
                        SettingsSwitchWidget(
                            icon = Icons.TwoTone.WifiOff,
                            title = stringResource(R.string.netisolate_title),
                            description = stringResource(R.string.netisolate_summary),
                            checked = uiState.enabled,
                            enabled = true,
                            onCheckedChange = { viewModel.setEnabled(it) },
                        )
                    }
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Info,
                            title = stringResource(R.string.netisolate_status_supported),
                            description = stringResource(R.string.netisolate_status_supported_yes),
                        )
                    }
                }
            }
        }

        // 标题行: 已阻止列表 + 右侧添加 UID 按钮
        item(key = "title_row") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = uidListTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onAddClick() }) {
                    Icon(
                        imageVector = Icons.TwoTone.Add,
                        contentDescription = stringResource(R.string.netisolate_add_uid),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // 已阻止列表 (仿 SUSFS susfsEntryList 机制: 添加行 + 列表项连一起, 缝分隔)
        if (uiState.selectedUids.isEmpty()) {
            item(key = "empty") {
                if (!pageLoaded || !uiState.loaded) {
                    // 加载前"无数据"占位 (仿 SUSFS)
                    Text(
                        text = stringResource(R.string.netisolate_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                } else {
                    // 空态 (SUSFS 机制: Inbox 图标 + 标题 + 描述)
                    Box(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .fillParentMaxHeight(0.5f)
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.Inbox,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.netisolate_no_uids),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        } else {
            lazySegmentColumn(
                items = uiState.selectedUids.sorted(),
                key = { _, uid -> uid },
            ) { _, uid ->
                val pkgs = remember(uid) {
                    runCatching { context.packageManager.getPackagesForUid(uid) }.getOrNull()
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
                SettingsBaseWidget(
                    title = label,
                    description = pkgName ?: "UID $uid",
                    leadingContent = {
                        PackageIcon(
                            packageName = pkgName ?: "",
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.TwoTone.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { viewModel.toggleUid(uid) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppPickerSheet(
    selectedUids: Set<Int>,
    onUidToggle: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val cardConfig: CardConfig = koinInject()
    val pm = context.packageManager
    var searchQuery by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    // 应用列表异步加载 (主线程查询几百应用会卡)
    var allApps by remember { mutableStateOf<List<AppListEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) {
            pm.getInstalledApplications(0)
                .map { appInfo ->
                    AppListEntry(
                        uid = appInfo.uid,
                        packageName = appInfo.packageName,
                        label = appInfo.loadLabel(pm).toString(),
                        packageInfo = runCatching { pm.getPackageInfo(appInfo.packageName, 0) }.getOrNull(),
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                    )
                }
                .distinctBy { it.uid }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }
    }

    val filteredApps = remember(searchQuery, showSystem, allApps) {
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
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
            // 标题行: 左上"选择应用", 右上"确定"
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.netisolate_pick_app),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.confirm))
                }
            }

            Spacer(Modifier.height(8.dp))

            // 搜索框 (支持文字/UID)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.netisolate_search_hint)) },
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

            // 显示系统应用选项
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = showSystem, onCheckedChange = { showSystem = it })
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.netisolate_show_system), style = MaterialTheme.typography.bodySmall)
            }

            // 应用列表 (复选框 + 分隔线)
            val dividerColor = MaterialTheme.colorScheme.outlineVariant
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(filteredApps, key = { it.uid }) { app ->
                    val isSelected = app.uid in selectedUids
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUidToggle(app.uid) }
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
                            checked = isSelected,
                            onCheckedChange = { onUidToggle(app.uid) },
                        )
                        Spacer(Modifier.width(8.dp))
                        AppIcon(
                            packageInfo = app.packageInfo,
                            context = context,
                            modifier = Modifier.size(36.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                app.label,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Text(
                            "UID ${app.uid}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(
    packageInfo: PackageInfo?,
    context: Context,
    modifier: Modifier = Modifier
) {
    val icon = remember(packageInfo) {
        packageInfo?.applicationInfo?.let {
            runCatching { it.loadIcon(context.packageManager) }.getOrNull()
        }
    }
    if (icon != null) {
        Image(
            bitmap = icon.toBitmap().asImageBitmap(),
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Spacer(modifier = modifier)
    }
}

private fun android.graphics.drawable.Drawable.toBitmap(): android.graphics.Bitmap {
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

// ==================== SUSFS 式子页组件 (工具箱内嵌复用) ====================

@Composable
internal fun NetIsolateStatusSubpage(
    uiState: NetIsolateUiState,
    innerPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    pageLoaded: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp,
            bottom = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SegmentedColumn {
                item {
                    SettingsSwitchWidget(
                        icon = Icons.TwoTone.WifiOff,
                        title = stringResource(R.string.netisolate_title),
                        description = stringResource(R.string.netisolate_summary),
                        checked = if (pageLoaded) uiState.enabled else false,
                        enabled = pageLoaded && uiState.loaded,
                        onCheckedChange = onEnabledChange,
                    )
                }
            }
        }
        item {
            SegmentedColumn {
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Info,
                        title = stringResource(R.string.netisolate_status_supported),
                        description = if (uiState.loaded) {
                            stringResource(R.string.netisolate_status_supported_yes)
                        } else {
                            stringResource(R.string.netisolate_no_data)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun NetIsolateUidListSubpage(
    uiState: NetIsolateUiState,
    innerPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    onAddClick: () -> Unit,
    onRemoveUid: (Int) -> Unit,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
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
                        title = stringResource(R.string.netisolate_add_uid),
                        description = stringResource(R.string.netisolate_add_uid_summary),
                        onClick = { onAddClick() },
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
        }
        if (uiState.selectedUids.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            if (uiState.loaded) R.string.netisolate_no_uids
                            else R.string.netisolate_no_data
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            uiState.selectedUids.sorted().forEach { uid ->
                item {
                    val pkgs = remember(uid) {
                        runCatching { context.packageManager.getPackagesForUid(uid) }.getOrNull()
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
                    SegmentedColumn {
                        item {
                            SettingsBaseWidget(
                                title = label,
                                description = pkgName ?: "UID $uid",
                                onClick = { onRemoveUid(uid) },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.TwoTone.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
