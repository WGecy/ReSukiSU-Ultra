package com.tesla.resukisuultra.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.Capsule
import kotlin.math.min

/**
 * G3 连续曲率圆角 — 委托 Kyant0 Shapes (已验证的曲率连续构造)
 *
 * RoundedCornerStyle.Continuous: 曲率从直边(0) 平滑过渡, 无任何拼接棱角。
 * API 与 RoundedCornerShape 兼容 (单/多参数, Int 像素构造)。
 */
@Immutable
class ContinuousCornerShape(
    private val topStart: Dp = 0.dp,
    private val topEnd: Dp = topStart,
    private val bottomEnd: Dp = topEnd,
    private val bottomStart: Dp = topStart,
) : Shape {

    /** 像素 Int 构造 (兼容 RoundedCornerShape(Int) 用法) */
    constructor(radius: Int) : this(radius.dp, radius.dp, radius.dp, radius.dp)

    private fun shape(density: Density, size: Size): Shape {
        val half = min(size.width, size.height) * 0.5f
        val rTL = with(density) { topStart.toPx() }.coerceIn(0f, half)
        val rTR = with(density) { topEnd.toPx() }.coerceIn(0f, half)
        val rBR = with(density) { bottomEnd.toPx() }.coerceIn(0f, half)
        val rBL = with(density) { bottomStart.toPx() }.coerceIn(0f, half)
        return RoundedRectangle(
            cornerRadius = maxOf(rTL, rTR, rBR, rBL).dp,
            style = RoundedCornerStyle.Continuous,
        )
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = shape(density, size).createOutline(size, layoutDirection, density)

    override fun toString(): String =
        "ContinuousCornerShape(topStart=$topStart, topEnd=$topEnd, " +
            "bottomEnd=$bottomEnd, bottomStart=$bottomStart)"
}

/** 全圆胶囊 + 连续曲率 (底栏等) */
@Immutable
class ContinuousCapsule : Shape {
    private val shape = Capsule(RoundedCornerStyle.Continuous)

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = shape.createOutline(size, layoutDirection, density)
}
