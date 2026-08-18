package com.tesla.resukisuultra.ui.screen.netisolate

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

import com.tesla.resukisuultra.ui.viewmodel.NetIsolateViewModel

import com.tesla.resukisuultra.ui.theme.blurEffect
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tesla.resukisuultra.R
import kotlinx.coroutines.async
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }

    // 仿 SUSFS: state 提升到 ViewModel (组合销毁不丢, 进入秒开, 后台刷新不闪)
    val viewModel: NetIsolateViewModel = koinViewModel()
    val netIsolateState by viewModel.uiState.collectAsStateWithLifecycle()
    val enabled = netIsolateState.enabled
    val selectedUids = netIsolateState.selectedUids
    val loaded = netIsolateState.loaded

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .nestedScroll(nestedScrollConnection),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            item {
                // 开关卡片 (圆角)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.netisolate_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.netisolate_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        enabled = loaded,
                        onCheckedChange = { viewModel.setEnabled(it) }
                    )
                }
            }

            if (enabled) {
                item {
                    // 已阻止列表标题 + 添加按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.netisolate_uid_list),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showPicker = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.netisolate_add_uid))
                        }
                    }
                }

                if (selectedUids.isEmpty() && loaded) {
                    item {
                        Text(
                            text = stringResource(R.string.netisolate_no_uids),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else if (!loaded) {
                    // 仿 SUSFS: 加载前显示"无数据"占位 (不转圈不误导)
                    item {
                        Text(
                            text = stringResource(R.string.netisolate_no_data),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(selectedUids.toList()) { uid ->
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

                        // 已阻止应用卡片 (圆角 + 图标)
                        val pkgInfo = remember(pkgName) {
                            pkgName?.let {
                                runCatching { context.packageManager.getPackageInfo(it, 0) }.getOrNull()
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(
                                packageInfo = pkgInfo,
                                context = context,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = pkgName ?: "UID $uid",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "UID $uid",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = { viewModel.toggleUid(uid) }) {
                                Icon(
                                    imageVector = Icons.TwoTone.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
    }

    if (showPicker) {
        AppPickerSheet(
            selectedUids = selectedUids,
            onUidToggle = { uid -> viewModel.toggleUid(uid) },
            onDismiss = { showPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
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
