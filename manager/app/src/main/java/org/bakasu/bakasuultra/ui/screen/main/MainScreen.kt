package org.bakasu.bakasuultra.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.bakasu.bakasuultra.ui.activity.component.NavigationBar
import org.bakasu.bakasuultra.ui.activity.component.rememberScrollConnection
import org.bakasu.bakasuultra.ui.component.HorizontalPagerWithInteraction
import org.bakasu.bakasuultra.ui.rememberMaterial3BlurBackdrop
import org.bakasu.bakasuultra.ui.screen.BottomBarDestination
import org.bakasu.bakasuultra.ui.theme.ThemeConfig
import org.bakasu.bakasuultra.ui.util.LocalBlurState
import org.bakasu.bakasuultra.ui.util.LocalHandlePageChange
import org.bakasu.bakasuultra.ui.util.LocalPagerPage
import org.bakasu.bakasuultra.ui.util.LocalPagerState
import org.bakasu.bakasuultra.ui.util.LocalSelectedPage
import org.bakasu.bakasuultra.ui.util.LocalSnackbarHost
import org.bakasu.bakasuultra.ui.viewmodel.HomeViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.pagerGestureOverride

@Composable
fun MainScreen(
    pagerInterceptionMode: Int = PagerInterceptionMode.CrossAxisInterceptor.ordinal,
) {
    val themeConfig: ThemeConfig = koinInject()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val pages = remember(homeState.systemStatus.isFullFeatured) {
        BottomBarDestination.getPages(homeState.systemStatus.isFullFeatured)
    }

    val coroutineScope = rememberCoroutineScope()
    var uiSelectedPage by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = uiSelectedPage,
        pageCount = { pages.size },
    )
    var userScrollEnabled by remember { mutableStateOf(true) }
    var animating by remember { mutableStateOf(false) }
    var animateJob by remember { mutableStateOf<Job?>(null) }
    var lastRequestedPage by remember { mutableIntStateOf(pagerState.currentPage) }

    val pagerMode = PagerInterceptionMode.entries.getOrElse(pagerInterceptionMode) {
        PagerInterceptionMode.Native
    }
    val interceptPagerGestures = pagerMode == PagerInterceptionMode.CrossAxisInterceptor

    val handlePageChange: (Int) -> Unit = remember(pagerState, coroutineScope, pages) {
        { page ->
            if (page !in pages.indices) return@remember
            uiSelectedPage = page

            if (page == pagerState.currentPage) {
                if (animateJob != null && lastRequestedPage != page) {
                    animateJob?.cancel()
                    animateJob = null
                    animating = false
                    userScrollEnabled = true
                }
                lastRequestedPage = page
            } else if (animateJob == null || lastRequestedPage != page) {
                animateJob?.cancel()
                animating = true
                userScrollEnabled = false
                lastRequestedPage = page
                animateJob = coroutineScope.launch {
                    try {
                        // A held pager gesture owns the scroll mutation at UserInput
                        // priority. Stop it explicitly so a navigation tap always wins.
                        pagerState.scroll(MutatePriority.PreventUserInput) { }
                        pagerState.animateScrollToPage(page)
                    } finally {
                        if (animateJob === this) {
                            animating = false
                            userScrollEnabled = true
                            animateJob = null
                            lastRequestedPage = pagerState.currentPage
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (!animating) uiSelectedPage = page
        }
    }

    BackHandler(pagerState.currentPage != 0) {
        handlePageChange(0)
    }

    CompositionLocalProvider(
        LocalPagerState provides pagerState,
        LocalHandlePageChange provides handlePageChange,
        LocalSelectedPage provides uiSelectedPage,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val isPortrait = maxWidth < maxHeight || (maxHeight / maxWidth > 1.4f)
            val content = @Composable { paddingBottom: Dp ->
                HorizontalPagerWithInteraction(
                    enableGestureOverride = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .pagerGestureOverride(
                            pagerState = pagerState,
                            mode = pagerMode,
                            enabled = userScrollEnabled,
                        ),
                    state = pagerState,
                    userScrollEnabled = userScrollEnabled && !interceptPagerGestures,
                    beyondViewportPageCount = if (homeState.isInitialDataLoaded) 1 else 0,
                    pageNestedScrollConnection = if (interceptPagerGestures) {
                        PagerGestureNestedScrollConnection
                    } else {
                        PagerDefaults.pageNestedScrollConnection(
                            state = pagerState,
                            orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                        )
                    },
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapAnimationSpec = PagerNavigationSpringSpec,
                    ),
                ) { pageIndex ->
                    if (pages.isEmpty()) return@HorizontalPagerWithInteraction

                    val snackBarHostState = remember { SnackbarHostState() }
                    CompositionLocalProvider(
                        LocalSnackbarHost provides snackBarHostState,
                        LocalPagerPage provides pageIndex,
                        LocalBlurState provides rememberMaterial3BlurBackdrop(
                            enableBlur = themeConfig.isEnableBlur,
                            pagerState = pagerState,
                            pagerPage = pageIndex,
                        ),
                    ) {
                        val destination = pages[pageIndex]
                        destination.direction(paddingBottom)
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
