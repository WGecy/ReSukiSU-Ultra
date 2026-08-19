package com.tesla.resukisuultra.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * G3 连续曲率圆角 (racra SmoothCorner 数学 — 贝塞尔+圆弧曲率匹配)
 * 每角: 2 段贝塞尔 (直边渐变, 起点曲率 0) + 圆弧 (曲率恒定, 与贝塞尔 G2 匹配)
 * 四角独立半径, API 兼容 RoundedCornerShape。
 */
@Immutable
class ContinuousCornerShape(
    private val topStart: Dp = 0.dp,
    private val topEnd: Dp = topStart,
    private val bottomEnd: Dp = topEnd,
    private val bottomStart: Dp = topStart,
    private val smoothness: Float = 0.6f,
) : Shape {

    /** 像素 Int 构造 (兼容 RoundedCornerShape(Int) 用法) */
    constructor(radius: Int) : this(radius.dp, radius.dp, radius.dp, radius.dp)

    @Volatile
    private var cacheKey: String? = null
    @Volatile
    private var cacheOutline: Outline? = null

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val half = min(size.height, size.width) / 2
        val rTL = with(density) { topStart.toPx() }.coerceIn(0f, half)
        val rTR = with(density) { topEnd.toPx() }.coerceIn(0f, half)
        val rBR = with(density) { bottomEnd.toPx() }.coerceIn(0f, half)
        val rBL = with(density) { bottomStart.toPx() }.coerceIn(0f, half)
        if (rTL + rTR + rBR + rBL == 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }
        val key = "${size.width.toInt()}|${size.height.toInt()}|" +
            "${(rTL * 100).toInt()},${(rTR * 100).toInt()}," +
            "${(rBR * 100).toInt()},${(rBL * 100).toInt()}"
        cacheKey?.let { if (it == key) return cacheOutline!! }
        val outline = buildSmoothOutline(size, rTL, rTR, rBR, rBL)
        cacheKey = key
        cacheOutline = outline
        return outline
    }

    private fun buildSmoothOutline(
        size: Size,
        rTL: Float,
        rTR: Float,
        rBR: Float,
        rBL: Float,
    ): Outline {
        val half = min(size.height, size.width) / 2
        val percent = (smoothness.coerceIn(0f, 1f) * 100).toInt()
        val w = size.width
        val h = size.height
        val path = Path()

        val t = SmoothCorner(rTL, percent, half)
        val r = SmoothCorner(rTR, percent, half)
        val b = SmoothCorner(rBR, percent, half)
        val l = SmoothCorner(rBL, percent, half)

        fun a1(s: SmoothCorner) = s.anchorPoint1
        fun c1(s: SmoothCorner) = s.controlPoint1
        fun c2(s: SmoothCorner) = s.controlPoint2
        fun a2(s: SmoothCorner) = s.anchorPoint2
        fun arc(s: SmoothCorner) = s.arcSection
        fun cx(p: PointRelativeToVertex) = p.distanceToClosestSide
        fun fx(p: PointRelativeToVertex) = p.distanceToFurthestSide

        // 左上
        path.moveTo(cx(a1(t)), fx(a1(t)))
        path.cubicTo(cx(c1(t)), fx(c1(t)), cx(c2(t)), fx(c2(t)), cx(a2(t)), fx(a2(t)))
        path.arcToRad(Rect(0f, 0f, arc(t).radius * 2, arc(t).radius * 2), (Math.toRadians(180.0) + arc(t).arcStartAngle).toFloat(), arc(t).arcSweepAngle, false)
        path.cubicTo(fx(c2(t)), cx(c2(t)), fx(c1(t)), cx(c1(t)), fx(a1(t)), cx(a1(t)))

        // 右上
        path.lineTo(w - fx(a1(r)), cx(a1(r)))
        path.cubicTo(w - fx(c1(r)), cx(c1(r)), w - fx(c2(r)), cx(c2(r)), w - fx(a2(r)), cx(a2(r)))
        path.arcToRad(Rect(w - arc(r).radius * 2, 0f, w, arc(r).radius * 2), (Math.toRadians(270.0) + arc(r).arcStartAngle).toFloat(), arc(r).arcSweepAngle, false)
        path.cubicTo(w - cx(c2(r)), fx(c2(r)), w - cx(c1(r)), fx(c1(r)), w - cx(a1(r)), fx(a1(r)))

        // 右下
        path.lineTo(w - cx(a1(b)), h - fx(a1(b)))
        path.cubicTo(w - cx(c1(b)), h - fx(c1(b)), w - cx(c2(b)), h - fx(c2(b)), w - cx(a2(b)), h - fx(a2(b)))
        path.arcToRad(Rect(w - arc(b).radius * 2, h - arc(b).radius * 2, w, h), (Math.toRadians(0.0) + arc(b).arcStartAngle).toFloat(), arc(b).arcSweepAngle, false)
        path.cubicTo(w - fx(c2(b)), h - cx(c2(b)), w - fx(c1(b)), h - cx(c1(b)), w - fx(a1(b)), h - cx(a1(b)))

        // 左下
        path.lineTo(fx(a1(l)), h - cx(a1(l)))
        path.cubicTo(fx(c1(l)), h - cx(c1(l)), fx(c2(l)), h - cx(c2(l)), fx(a2(l)), h - cx(a2(l)))
        path.arcToRad(Rect(0f, h - arc(l).radius * 2, arc(l).radius * 2, h), (Math.toRadians(90.0) + arc(l).arcStartAngle).toFloat(), arc(l).arcSweepAngle, false)
        path.cubicTo(cx(c2(l)), h - fx(c2(l)), cx(c1(l)), h - fx(c1(l)), cx(a1(l)), h - fx(a1(l)))

        path.close()
        return Outline.Generic(path)
    }

    override fun toString(): String =
        "ContinuousCornerShape(topStart=$topStart, topEnd=$topEnd, " +
            "bottomEnd=$bottomEnd, bottomStart=$bottomStart, smoothness=$smoothness)"
}

/** 全圆胶囊 + 连续曲率 (底栏等) */
@Immutable
class ContinuousCapsule(
    private val smoothness: Float = 0.6f,
) : Shape {
    @Volatile
    private var cacheKey: String? = null
    @Volatile
    private var cacheOutline: Outline? = null

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val key = "${size.width.toInt()}|${size.height.toInt()}"
        cacheKey?.let { if (it == key) return cacheOutline!! }
        val r = min(size.width, size.height) * 0.5f
        val shape = ContinuousCornerShape(r.dp, r.dp, r.dp, r.dp, smoothness)
        val outline = shape.createOutline(size, layoutDirection, density)
        cacheKey = key
        cacheOutline = outline
        return outline
    }
}

/** 菜单选中项圆角 (Material 设计: 首尾贴合菜单角 18dp, 中间扁平; 与 28dp 菜单组留白) */
fun menuItemShapes(index: Int, count: Int): androidx.compose.material3.MenuItemShapes =
    androidx.compose.material3.MenuItemShapes(
        ContinuousCornerShape(
            topStart = if (index == 0) 18.dp else 0.dp,
            topEnd = if (index == 0) 18.dp else 0.dp,
            bottomStart = if (index == count - 1) 18.dp else 0.dp,
            bottomEnd = if (index == count - 1) 18.dp else 0.dp,
        ),
        ContinuousCornerShape(18.dp),
    )

// ===== racra SmoothCorner 数学 (G2 曲率连续: 贝塞尔+圆弧匹配) =====

internal class SmoothCorner(
    private val cornerRadius: Float,
    private val smoothnessAsPercent: Int,
    private val maximumCurveStartDistanceFromVertex: Float,
) {
    private val radius = min(cornerRadius, maximumCurveStartDistanceFromVertex)
    private val smoothness = smoothnessAsPercent / 100f
    private val curveStartDistance = min(maximumCurveStartDistanceFromVertex, (1 + smoothness) * radius)
    private val shouldCurveInterpolate = radius <= maximumCurveStartDistanceFromVertex / 2
    private val interpolationMultiplier =
        (radius - maximumCurveStartDistanceFromVertex / 2) / (maximumCurveStartDistanceFromVertex / 2)

    private val angleAlpha =
        if (shouldCurveInterpolate) Math.toRadians(45.0 * smoothness).toFloat()
        else Math.toRadians(45.0 * smoothness * (1 - interpolationMultiplier)).toFloat()
    private val angleBeta =
        if (shouldCurveInterpolate) Math.toRadians(90.0 * (1.0 - smoothness)).toFloat()
        else Math.toRadians(90.0 * (1 - smoothness * (1 - interpolationMultiplier))).toFloat()
    private val angleTheta = ((Math.toRadians(90.0) - angleBeta) / 2.0).toFloat()

    private val distanceE = radius * tan(angleTheta / 2)
    private val distanceC = distanceE * cos(angleAlpha)
    private val distanceD = distanceC * tan(angleAlpha)
    private val distanceK = sin(angleBeta / 2) * radius
    private val distanceL = distanceK * sqrt(2.0f)
    private val distanceB = ((curveStartDistance - distanceL) - (1 + tan(angleAlpha)) * distanceC) / 3f
    private val distanceA = 2 * distanceB

    val anchorPoint1 = PointRelativeToVertex(min(curveStartDistance, maximumCurveStartDistanceFromVertex), 0f)
    val controlPoint1 = PointRelativeToVertex(anchorPoint1.distanceToFurthestSide - distanceA, 0f)
    val controlPoint2 = PointRelativeToVertex(controlPoint1.distanceToFurthestSide - distanceB, 0f)
    val anchorPoint2 = PointRelativeToVertex(controlPoint2.distanceToFurthestSide - distanceC, distanceD)
    val arcSection = Arc(radius, angleTheta, angleBeta)
}

internal data class PointRelativeToVertex(
    val distanceToFurthestSide: Float,
    val distanceToClosestSide: Float,
)

internal data class Arc(
    val radius: Float,
    val arcStartAngle: Float,
    val arcSweepAngle: Float,
)
