package com.tesla.resukisuultra.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tesla.resukisuultra.ui.activity.component.NavigationBar
import com.tesla.resukisuultra.ui.activity.component.rememberScrollConnection
import com.tesla.resukisuultra.ui.rememberMaterial3BlurBackdrop
import com.tesla.resukisuultra.ui.screen.BottomBarDestination
import com.tesla.resukisuultra.ui.theme.ThemeConfig
import com.tesla.resukisuultra.ui.theme.blurSource
import com.tesla.resukisuultra.ui.util.LocalBlurState
import com.tesla.resukisuultra.ui.util.LocalHandlePageChange
import com.tesla.resukisuultra.ui.util.LocalPagerPage
import com.tesla.resukisuultra.ui.util.LocalPagerState
import com.tesla.resukisuultra.ui.util.LocalSelectedPage
import com.tesla.resukisuultra.ui.util.LocalSnackbarHost
import com.tesla.resukisuultra.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun MainScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val pages = remember(homeState.systemStatus.isValid, homeState.systemInfo.kpmSupported) {
        BottomBarDestination.getPages(
            homeState.systemStatus.isValid,
            kpmSupported = homeState.systemInfo.kpmSupported,
        )
    }

    var uiSelectedPage by rememberSaveable { mutableIntStateOf(0) }
    val handlePageChange: (Int) -> Unit = { page ->
        uiSelectedPage = page
    }

    BackHandler(uiSelectedPage != 0) {
        uiSelectedPage = 0
    }

    CompositionLocalProvider(
        LocalHandlePageChange provides handlePageChange,
        LocalSelectedPage provides uiSelectedPage
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val isPortrait = maxWidth < maxHeight || (maxHeight / maxWidth > 1.4f)
            val content = @Composable { paddingBottom: Dp ->
                // folkx 引擎切换 (照搬 FolkPatch linear: 左右滑动 + spring(0.8,300) + fade)
                if (pages.isNotEmpty()) {
                AnimatedContent(
                    targetState = uiSelectedPage,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f), initialOffsetX = { it })) togetherWith
                                (slideOutHorizontally(animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f), targetOffsetX = { -it }))
                        } else {
                            (slideInHorizontally(animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f), initialOffsetX = { -it })) togetherWith
                                (slideOutHorizontally(animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f), targetOffsetX = { it }))
                        }
                    },
                ) { pageIndex ->
                    val snackBarHostState = remember { SnackbarHostState() }
                    CompositionLocalProvider(
                        LocalSnackbarHost provides snackBarHostState,
                    ) {
                        val destination = pages[pageIndex]
                        destination.direction(paddingBottom)
                    }
                }
                }
            }

            if (isPortrait) {
                // 悬浮底栏滚动隐藏 (向下滑隐藏, 向上滑显示)
                val isScrollingDown = remember { mutableStateOf(false) }
                val scrollOffset = remember { mutableStateOf(0f) }
                val previousScrollOffset = remember { mutableStateOf(0f) }
                val scrollConnection = rememberScrollConnection(
                    isScrollingDown, scrollOffset, previousScrollOffset
                )
                val barOffsetY = remember { Animatable(0f) }
                LaunchedEffect(isScrollingDown.value) {
                    barOffsetY.animateTo(
                        targetValue = if (isScrollingDown.value) 1f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        Box(
                            modifier = Modifier.graphicsLayer {
                                translationY = barOffsetY.value * 120.dp.toPx()
                            }
                        ) {
                            NavigationBar(
                                destinations = pages,
                                isBottomBar = true,
                            )
                        }
                    },
                    containerColor = Color.Transparent,
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollConnection)
                    ) {
                        content(innerPadding.calculateBottomPadding())
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationBar(
                        destinations = pages,
                        isBottomBar = false,
                    )
                    content(0.dp)
                }
            }
        }
    }
}
