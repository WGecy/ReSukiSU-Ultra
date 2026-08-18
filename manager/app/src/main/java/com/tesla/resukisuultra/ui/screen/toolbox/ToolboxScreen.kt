package com.tesla.resukisuultra.ui.screen.toolbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tesla.resukisuultra.R
import com.tesla.resukisuultra.ui.component.settings.AppBackButton
import com.tesla.resukisuultra.ui.navigation.LocalNavigator
import org.koin.compose.koinInject
import com.tesla.resukisuultra.data.shell.KsuCliRepository
import com.tesla.resukisuultra.ui.screen.netisolate.NetIsolateTab
import com.tesla.resukisuultra.ui.theme.CardConfig
import com.tesla.resukisuultra.ui.theme.ThemeConfig
import com.tesla.resukisuultra.ui.theme.blurEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 附加功能 (工具箱): SUSFS 同款 Tab 布局
 * 后续新功能在此追加 tab 子页面
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolboxScreen() {
    val navigator = LocalNavigator.current
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val coroutineScope = rememberCoroutineScope()

    // 内核支持检测: netisolate 只在内核集成时显示 (异步检测, 避免主线程 exec 阻塞)
    val context = LocalContext.current
    var netisolateSupported by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        netisolateSupported = withContext(Dispatchers.IO) {
            runCatching {
                KsuCliRepository(context).exec("/data/adb/ksu/bin/ksud feature check netisolate")
                    ?.contains("supported") == true
            }.getOrDefault(false)
        }
    }

    val subpages = buildList {
        if (netisolateSupported) {
            add(ToolboxSubpage(
                title = stringResource(R.string.netisolate_title),
            ) { innerPadding, nestedScrollConnection ->
                NetIsolateTab(
                    innerPadding = innerPadding,
                    nestedScrollConnection = nestedScrollConnection,
                )
            })
        }
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { subpages.size },
    )

    Scaffold(
        topBar = {
            Column(modifier = Modifier.blurEffect()) {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.toolbox_title)) },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        AppBackButton(
                            onClick = { navigator.pop() }
                        )
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
                                MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha)
                    ),
                    windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                )

                // 注意: subpages 为空 (支持检测中) 时不渲染 TabRow — 空 tabs 会 IndexOutOfBounds 崩溃
                if (subpages.isNotEmpty()) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor =
                        if (themeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                    edgePadding = 0.dp,
                    minTabWidth = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    subpages.forEachIndexed { index, subpage ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
                        ) {
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                modifier = Modifier.widthIn(
                                    min = TabRowDefaults.ScrollableTabRowMinTabWidth
                                ),
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                text = { Text(subpage.title) }
                            )
                        }
                    }
                }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        if (subpages.isEmpty()) {
            // 支持检测中 — 占位 (不渲染空 Pager, 防空 pageCount 崩溃)
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        } else {
            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = pagerState,
                beyondViewportPageCount = 1,
            ) { page ->
                subpages[page].content(innerPadding, scrollBehavior.nestedScrollConnection)
            }
        }
    }
}

private class ToolboxSubpage(
    val title: String,
    val content: @Composable (androidx.compose.foundation.layout.PaddingValues, androidx.compose.ui.input.nestedscroll.NestedScrollConnection) -> Unit,
)
