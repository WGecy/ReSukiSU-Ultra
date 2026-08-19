package com.tesla.resukisuultra.ui.activity.component

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import io.github.fletchmckee.liquid.liquid
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailColors
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tesla.resukisuultra.ui.screen.BottomBarDestination
import com.tesla.resukisuultra.ui.theme.CardConfig
import com.tesla.resukisuultra.ui.theme.ThemeConfig
import com.tesla.resukisuultra.ui.theme.blurEffect
import com.tesla.resukisuultra.ui.util.LocalHandlePageChange
import com.tesla.resukisuultra.ui.util.LocalSelectedPage
import com.tesla.resukisuultra.ui.viewmodel.HomeViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

// TODO Add FloatingBottomBar as an choice to user

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavigationBar(
    liquidState: io.github.fletchmckee.liquid.LiquidState? = null,
    destinations: List<BottomBarDestination>,
    isBottomBar: Boolean
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    // 是否隐藏 badge
    val homeViewModel = koinViewModel<HomeViewModel>()
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val isHideOtherInfo = uiState.isHideOtherInfo
    val superuserCount = uiState.systemInfo.superuserCount
    val moduleCount = uiState.systemInfo.moduleCount

    // 翻页处理
    val page = LocalSelectedPage.current
    val handlePageChange = LocalHandlePageChange.current

    if (isBottomBar) {
        FloatingBottomBar(
            liquidState = liquidState,
            destinations = destinations,
            page = page,
            onPageChange = handlePageChange,
            superuserCount = superuserCount,
            moduleCount = moduleCount,
            isHideOtherInfo = isHideOtherInfo,
        )
    } else {
        WideNavigationRail(
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                )
                .blurEffect(),
            colors = WideNavigationRailColors(
                containerColor =
                    if (themeConfig.isEnableBlur)
                        Color.Transparent
                    else
                        MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modalContainerColor = WideNavigationRailDefaults.colors().modalContainerColor,
                modalScrimColor = WideNavigationRailDefaults.colors().modalScrimColor,
                modalContentColor = WideNavigationRailDefaults.colors().modalContentColor,
            ),
        ) {
            destinations.forEachIndexed { index, destination ->
                NavigationRailItem(
                    isSelected = index == page,
                    destination = destination,
                    onClick = {
                        handlePageChange(index)
                    },
                    superuserCount = superuserCount,
                    moduleCount = moduleCount,
                    isHideOtherInfo = isHideOtherInfo,
                )
            }
        }
    }
}

@Composable
private fun FloatingBottomBar(
    liquidState: io.github.fletchmckee.liquid.LiquidState? = null,
    destinations: List<BottomBarDestination>,
    page: Int,
    onPageChange: (Int) -> Unit,
    superuserCount: Int,
    moduleCount: Int,
    isHideOtherInfo: Boolean,
) {
    // 选中项滑动动画 (FolkPatch 风格弹跳)
    val animatedIndex = remember { Animatable(page.toFloat()) }
    LaunchedEffect(page) {
        animatedIndex.animateTo(
            targetValue = page.toFloat(),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    val itemSize = 52.dp
    val itemSpacing = 6.dp
    val containerPadding = 8.dp
    val barHeight = 68.dp
    val barWidth = (itemSize * destinations.size) +
        (itemSpacing * (destinations.size - 1)) +
        (containerPadding * 2)

    // 纯色 + 动态取色 (MaterialKolor dynamicColorScheme)
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val indicatorColor = MaterialTheme.colorScheme.secondaryContainer
    val selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer
    val unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = with(LocalDensity.current) {
                WindowInsets.navigationBars.getBottom(this).toDp()
            })
    ) {
        val screenWidth = maxWidth
        val horizontalScreenPadding = when {
            screenWidth > 600.dp -> 32.dp
            screenWidth > 400.dp -> 24.dp
            else -> 16.dp
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalScreenPadding, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .width(barWidth)
                    .height(barHeight)
                    .then(
                        if (liquidState != null) Modifier.liquid(liquidState) else Modifier
                    ),
                shape = CircleShape,
                color = containerColor,
                tonalElevation = 3.dp,
                shadowElevation = 12.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = containerPadding)
                ) {
                    // 滑动指示器 (offset = item 位置, 与 Row 对齐)
                    val density = LocalDensity.current
                    val itemSizePx = with(density) { itemSize.toPx() }
                    val itemSpacingPx = with(density) { itemSpacing.toPx() }
                    val indicatorOffset = (itemSizePx + itemSpacingPx) * animatedIndex.value

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 8.dp)
                            .offset {
                                IntOffset(
                                    x = indicatorOffset.toInt(),
                                    y = 0,
                                )
                            }
                            .width(itemSize)
                            .clip(CircleShape)
                            .background(indicatorColor),
                    )

                    // 图标项 (clip 先行 → 圆形点击波纹)
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        destinations.forEachIndexed { index, destination ->
                            Box(
                                modifier = Modifier
                                    .size(itemSize)
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = remember {
                                            androidx.compose.foundation.interaction.MutableInteractionSource()
                                        },
                                        indication = androidx.compose.material3.ripple(),
                                    ) { onPageChange(index) },
                                contentAlignment = Alignment.Center,
                            ) {
                                BadgedBox(
                                    badge = {
                                        DestinationBadge(
                                            dest = destination,
                                            superUser = superuserCount,
                                            module = moduleCount,
                                            isHideOtherInfo = isHideOtherInfo,
                                        )
                                    }
                                ) {
                                    Icon(
                                        if (index == page) {
                                            destination.iconSelected
                                        } else {
                                            destination.iconNotSelected
                                        },
                                        stringResource(destination.label),
                                        tint = if (index == page) {
                                            selectedIconColor
                                        } else {
                                            unselectedIconColor
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationRailItem(
    isSelected: Boolean,
    destination: BottomBarDestination,
    onClick: () -> Unit,
    superuserCount: Int,
    moduleCount: Int,
    isHideOtherInfo: Boolean
) {
    WideNavigationRailItem(
        railExpanded = false,
        selected = isSelected,
        onClick = onClick,
        icon = {
            BadgedBox(
                badge = {
                    DestinationBadge(
                        dest = destination,
                        superUser = superuserCount,
                        module = moduleCount,
                        isHideOtherInfo = isHideOtherInfo,
                    )
                }
            ) {
                if (isSelected) {
                    Icon(destination.iconSelected, stringResource(destination.label))
                } else {
                    Icon(destination.iconNotSelected, stringResource(destination.label))
                }
            }
        },
        label = {
            Text(
                stringResource(destination.label),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible
            )
        },
    )
}

@Composable
private fun DestinationBadge(
    dest: BottomBarDestination,
    superUser: Int,
    module: Int,
    isHideOtherInfo: Boolean
) {
    val count = when (dest) {
        BottomBarDestination.SuperUser -> superUser
        BottomBarDestination.Module -> module
        else -> 0
    }

    AnimatedVisibility(
        visible = count > 0 && !isHideOtherInfo,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Badge(
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Text(count.toString())
        }
    }
}
