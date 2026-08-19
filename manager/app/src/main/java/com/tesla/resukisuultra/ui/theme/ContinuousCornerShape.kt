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
 * 自研 G2 连续曲率圆角 (racra SmoothCorner 构造 — 贝塞尔 + 圆弧曲率匹配)
 *
 * 每角 = 2 段贝塞尔 (直边渐变) + 1 段圆弧 (曲率恒定):
 * - 贝塞尔起点曲率 0 (与直线 G2 连续 — 无衔接突变)
 * - 贝塞尔终点与圆弧曲率匹配 (G2 段间连续 — 无棱角)
 * - 视觉半径 = r (与圆弧一致, 圆角大小不变)
 *
 * @param smoothness 0~1: 圆弧占比 (1 = 纯圆弧, 0.65 ≈ iOS 观感)
 */
@Immutable
class ContinuousCornerShape(
    private val topStart: Dp = 0.dp,
    private val topEnd: Dp = topStart,
    private val bottomEnd: Dp = topEnd,
    private val bottomStart: Dp = topStart,
    private val smoothness: Float = 0.65f,
) : Shape {

    /** 像素 Int 构造 (兼容 RoundedCornerShape(Int) 用法) */
    constructor(
        radius: Int,
        smoothness: Float = 0.65f,
    ) : this(radius.dp, radius.dp, radius.dp, radius.dp, smoothness)

    @Volatile
    private var cacheKey: String? = null
    @Volatile
    private var cacheOutline: Outline? = null

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val half = min(size.width, size.height) * 0.5f
        val rTL = with(density) { topStart.toPx() }.coerceIn(0f, half)
        val rTR = with(density) { topEnd.toPx() }.coerceIn(0f, half)
        val rBR = with(density) { bottomEnd.toPx() }.coerceIn(0f, half)
        val rBL = with(density) { bottomStart.toPx() }.coerceIn(0f, half)
        if (rTL == 0f && rTR == 0f && rBR == 0f && rBL == 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }
        val key = "${size.width.toInt()}|${size.height.toInt()}|" +
            "${(rTL * 100).toInt()},${(rTR * 100).toInt()}," +
            "${(rBR * 100).toInt()},${(rBL * 100).toInt()}"
        cacheKey?.let { if (it == key) return cacheOutline!! }

        val outline = buildOutline(size, rTL, rTR, rBR, rBL)
        cacheKey = key
        cacheOutline = outline
        return outline
    }

    private fun buildOutline(
        size: Size,
        rTL: Float,
        rTR: Float,
        rBR: Float,
        rBL: Float,
    ): Outline {
        val w = size.width
        val h = size.height
        val half = min(w, h) * 0.5f
        val path = Path()

        // 四角平滑构造 (贝塞尔 + 圆弧, G2 曲率连续)
        val cTL = CornerGeom(rTL, half, smoothness)
        val cTR = CornerGeom(rTR, half, smoothness)
        val cBR = CornerGeom(rBR, half, smoothness)
        val cBL = CornerGeom(rBL, half, smoothness)

        // 左上: 从左边到顶边
        path.moveTo(cTL.closest, cTL.furthest)
        path.cubicTo(cTL.c1Closest, cTL.c1Furthest, cTL.c2Closest, cTL.c2Furthest, cTL.arcClosest, cTL.arcFurthest)
        path.arcToRad(
            rect = Rect(0f, 0f, cTL.radius * 2, cTL.radius * 2),
            startAngleRadians = (Math.PI + cTL.theta).toFloat(),
            sweepAngleRadians = cTL.beta,
            forceMoveTo = false,
        )
        path.cubicTo(cTL.c2Furthest, cTL.c2Closest, cTL.c1Furthest, cTL.c1Closest, cTL.furthest, cTL.closest)

        // 顶边 → 右上
        path.lineTo(w - cTR.furthest, cTR.closest)
        path.cubicTo(w - cTR.c1Furthest, cTR.c1Closest, w - cTR.c2Furthest, cTR.c2Closest, w - cTR.arcFurthest, cTR.arcClosest)
        path.arcToRad(
            rect = Rect(w - cTR.radius * 2, 0f, w, cTR.radius * 2),
            startAngleRadians = (Math.PI * 1.5 + cTR.theta).toFloat(),
            sweepAngleRadians = cTR.beta,
            forceMoveTo = false,
        )
        path.cubicTo(w - cTR.c2Closest, cTR.c2Furthest, w - cTR.c1Closest, cTR.c1Furthest, w - cTR.closest, cTR.furthest)

        // 右边 → 右下
        path.lineTo(w - cBR.closest, h - cBR.furthest)
        path.cubicTo(w - cBR.c1Closest, h - cBR.c1Furthest, w - cBR.c2Closest, h - cBR.c2Furthest, w - cBR.arcClosest, h - cBR.arcFurthest)
        path.arcToRad(
            rect = Rect(w - cBR.radius * 2, h - cBR.radius * 2, w, h),
            startAngleRadians = (Math.PI + cBR.theta).toFloat(),
            sweepAngleRadians = cBR.beta,
            forceMoveTo = false,
        )
        path.cubicTo(w - cBR.c2Furthest, h - cBR.c2Closest, w - cBR.c1Furthest, h - cBR.c1Closest, w - cBR.furthest, h - cBR.closest)

        // 底边 → 左下
        path.lineTo(cBL.furthest, h - cBL.closest)
        path.cubicTo(cBL.c1Furthest, h - cBL.c1Closest, cBL.c2Furthest, h - cBL.c2Closest, cBL.arcFurthest, h - cBL.arcClosest)
        path.arcToRad(
            rect = Rect(0f, h - cBL.radius * 2, cBL.radius * 2, h),
            startAngleRadians = (Math.PI * 0.5 + cBL.theta).toFloat(),
            sweepAngleRadians = cBL.beta,
            forceMoveTo = false,
        )
        path.cubicTo(cBL.c2Closest, h - cBL.c2Furthest, cBL.c1Closest, h - cBL.c1Furthest, cBL.closest, h - cBL.furthest)

        path.close()
        return Outline.Generic(path)
    }

    /**
     * 单角平滑几何 (racra SmoothCorner 数学 — G2 曲率连续)
     * 坐标: closest = 靠近当前边(角内 x), furthest = 远离(角内 y)
     */
    private class CornerGeom(radius: Float, maxDist: Float, smoothness: Float) {
        val radius = min(radius, maxDist)
        private val sm = (smoothness.coerceIn(0f, 1f) * 100).toInt() / 100f
        val beta = (Math.toRadians(90.0 * (1.0 - sm))).toFloat() // 圆弧扫角
        val theta = ((Math.toRadians(90.0) - beta) / 2.0).toFloat() // 圆弧起角
        private val distanceE = radius * tan(theta / 2)
        private val alpha = Math.toRadians(45.0 * sm).toFloat()
        private val distanceC = distanceE * cos(alpha)
        private val distanceD = distanceC * tan(alpha)
        private val distanceK = sin(beta / 2) * radius
        private val distanceL = distanceK * sqrt(2.0f)
        private val curveStart = min(maxDist, (1f + sm) * radius)
        private val distanceB = ((curveStart - distanceL) - (1f + tan(alpha)) * distanceC) / 3f
        private val distanceA = 2f * distanceB

        // 直边锚点 (起始)
        val closest = min(curveStart, maxDist)
        val furthest = radius
        // 贝塞尔控制点 1
        val c1Closest = closest - distanceA
        val c1Furthest = radius
        // 贝塞尔控制点 2
        val c2Closest = c1Closest - distanceB
        val c2Furthest = radius
        // 圆弧起点 (贝塞尔终点)
        val arcClosest = c2Closest - distanceC
        val arcFurthest = radius - distanceD
    }

    override fun toString(): String =
        "ContinuousCornerShape(topStart=$topStart, topEnd=$topEnd, " +
            "bottomEnd=$bottomEnd, bottomStart=$bottomStart, smoothness=$smoothness)"
}

/** 全圆胶囊 + 连续曲率 (底栏等) */
@Immutable
class ContinuousCapsule(
    private val smoothness: Float = 0.65f,
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
