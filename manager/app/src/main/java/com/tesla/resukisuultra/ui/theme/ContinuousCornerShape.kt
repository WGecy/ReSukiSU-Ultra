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
import kotlin.math.min

/**
 * 自研 G3 连续曲率圆角 (对标 iOS 观感)
 *
 * 每角 3 段 30° 贝塞尔: 视觉半径与圆弧一致 (圆角大小不变),
 * 端点曲率=0 (与直线 G2 连续, 衔接平滑), 段间曲率渐进 (无棱角)。
 *
 * @param smoothness 0 = 标准圆弧, 1 = 方润内收, 默认 0.5 (iOS 观感)
 */
@Immutable
class ContinuousCornerShape(
    private val topStart: Dp = 0.dp,
    private val topEnd: Dp = topStart,
    private val bottomEnd: Dp = topEnd,
    private val bottomStart: Dp = topStart,
    private val smoothness: Float = 0.5f,
) : Shape {

    /** 像素 Int 构造 (兼容 RoundedCornerShape(Int) 用法) */
    constructor(
        radius: Int,
        smoothness: Float = 0.5f,
    ) : this(radius.dp, radius.dp, radius.dp, radius.dp, smoothness)

    // 30° 弧贝塞尔系数 4/3*tan(7.5°)≈0.1754 → G3 内收 0.149
    private val k = 0.1754f + (0.149f - 0.1754f) * smoothness.coerceIn(0f, 1f)

    @Volatile
    private var cacheKey: String? = null
    @Volatile
    private var cacheOutline: Outline? = null

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val rTL = with(density) { topStart.toPx() }.coerceIn(0f, min(size.width, size.height) * 0.5f)
        val rTR = with(density) { topEnd.toPx() }.coerceIn(0f, min(size.width, size.height) * 0.5f)
        val rBR = with(density) { bottomEnd.toPx() }.coerceIn(0f, min(size.width, size.height) * 0.5f)
        val rBL = with(density) { bottomStart.toPx() }.coerceIn(0f, min(size.width, size.height) * 0.5f)
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
        val path = Path()
        path.moveTo(rTL, 0f)
        path.lineTo(w - rTR, 0f)
        addCorner(path, w - rTR, rTR, rTR, 0) // 右上: 顶边→右边
        path.lineTo(w, h - rBR)
        addCorner(path, w - rBR, h - rBR, rBR, 1) // 右下: 右边→底边
        path.lineTo(rBL, h)
        addCorner(path, rBL, h - rBL, rBL, 2) // 左下: 底边→左边
        path.lineTo(0f, rTL)
        addCorner(path, rTL, rTL, rTL, 3) // 左上: 左边→顶边
        path.close()
        return Outline.Generic(path)
    }

    /** 角段: 3 段 30° 贝塞尔 (标准右上角段 + 旋转到四角) */
    private fun addCorner(
        path: Path,
        cx: Float,
        cy: Float,
        r: Float,
        mode: Int,
    ) {
        // 标准右上角段点 (θ=0°,30°,60°,90°): (r·sinθ, -r·cosθ)
        val pts = arrayOf(
            floatArrayOf(0f, -r),
            floatArrayOf(0.5f * r, -0.8660254f * r),
            floatArrayOf(0.8660254f * r, -0.5f * r),
            floatArrayOf(r, 0f),
        )
        // 各点切线 (旋转 90°): (cosθ, sinθ)
        val tans = arrayOf(
            floatArrayOf(1f, 0f),
            floatArrayOf(0.8660254f, 0.5f),
            floatArrayOf(0.5f, 0.8660254f),
            floatArrayOf(0f, 1f),
        )
        for (i in 0 until 3) {
            val kMul = if (i == 0 || i == 2) 0.75f else 1f
            val (a0x, a0y) = mapPoint(mode, cx, cy, pts[i][0], pts[i][1])
            val (a3x, a3y) = mapPoint(mode, cx, cy, pts[i + 1][0], pts[i + 1][1])
            val (t0x, t0y) = mapDir(mode, tans[i][0], tans[i][1])
            val (t3x, t3y) = mapDir(mode, tans[i + 1][0], tans[i + 1][1])
            path.cubicTo(
                a0x + t0x * k * kMul * r, a0y + t0y * k * kMul * r,
                a3x - t3x * k * kMul * r, a3y - t3y * k * kMul * r,
                a3x, a3y,
            )
        }
    }

    /** 角内点映射到绝对坐标 (0右上 1右下 2左下 3左上) */
    private fun mapPoint(mode: Int, cx: Float, cy: Float, x: Float, y: Float): Pair<Float, Float> =
        when (mode) {
            0 -> cx + x to cy + y
            1 -> cx - y to cy + x
            2 -> cx - x to cy - y
            else -> cx + y to cy - x
        }

    /** 切线方向映射 (只旋转不平移) */
    private fun mapDir(mode: Int, tx: Float, ty: Float): Pair<Float, Float> =
        when (mode) {
            0 -> tx to ty
            1 -> -ty to tx
            2 -> -tx to -ty
            else -> ty to -tx
        }

    override fun toString(): String =
        "ContinuousCornerShape(topStart=$topStart, topEnd=$topEnd, " +
            "bottomEnd=$bottomEnd, bottomStart=$bottomStart, smoothness=$smoothness)"
}

/** 全圆胶囊 + 连续曲率 (底栏等) */
@Immutable
class ContinuousCapsule(
    private val smoothness: Float = 0.5f,
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
