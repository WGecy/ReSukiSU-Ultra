package com.tesla.resukisuultra.ui.animation.predictiveback

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEventTransitionState

class KernelSUClassicPredictiveBackAnimation : PredictiveBackAnimationHandler {
    override suspend fun onBackPressed(
        transitionState: NavigationEventTransitionState?,
        currentPageKey: NavKey?
    ) {
        // ignore
    }

    @Composable
    override fun Modifier.predictiveBackAnimationDecorator(
        transitionState: NavigationEventTransitionState?,
        contentPageKey: Any,
        currentPageKey: NavKey?,
    ): Modifier {
        return this
    }

    override fun AnimatedContentTransitionScope<Scene<NavKey>>.onPredictivePopTransitionSpec(
        swipeEdge: Int
    ): ContentTransform =
        ContentTransform(
            targetContentEnter = slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow), initialOffsetX = { fullWidth -> -fullWidth }),
            initialContentExit = scaleOut(spring(stiffness = Spring.StiffnessMediumLow), targetScale = 0.9f) + fadeOut(spring(stiffness = Spring.StiffnessMediumLow)),
            sizeTransform = null
        )

    override fun AnimatedContentTransitionScope<Scene<NavKey>>.onPopTransitionSpec(): ContentTransform =
        ContentTransform(
            targetContentEnter = slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow), initialOffsetX = { fullWidth -> -fullWidth }),
            initialContentExit = scaleOut(spring(stiffness = Spring.StiffnessMediumLow), targetScale = 0.9f) + fadeOut(spring(stiffness = Spring.StiffnessMediumLow)),
            sizeTransform = null
        )

    override fun AnimatedContentTransitionScope<Scene<NavKey>>.onTransitionSpec(): ContentTransform =
        ContentTransform(
            targetContentEnter = slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow), initialOffsetX = { fullWidth -> fullWidth }),
            initialContentExit = slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow), targetOffsetX = { fullWidth -> -fullWidth }),
            sizeTransform = null
        )
}