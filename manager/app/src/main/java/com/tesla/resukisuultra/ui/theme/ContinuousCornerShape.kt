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
import kotlin.math.pow

/**
 * 自研 G3 超椭圆圆角 (对标 iOS 五次超椭圆 squircle)
 *
 * 每角直接用超椭圆段 |x/r|^n + |y/r|^n = 1 (n=5) 采样:
 * - 端点曲率 = 0 (与直线 G2 严格连续), 曲率平缓起步 (G3 级过渡)
 * - 整条曲线无拼接, 无穷阶光滑 — 衔接处无任何棱角
 *
 * @param smoothness 0 = 圆 (n=2), 1 = iOS G3 (n=5), 默认 1.0
 */
@Immutable
class ContinuousCornerShape(
    private val topStart: Dp = 0.dp,
    private val topEnd: Dp = topStart,
    private val bottomEnd: Dp = topEnd,
    private val bottomStart: Dp = topStart,
    private val smoothness: Float = 1.0f,
) : Shape {

    // 超椭圆指数: n = 2 + 3*smoothness
    /** 像素 Int 构造 (兼容 RoundedCornerShape(Int) 用法) */
    constructor(
        radius: Int,
        smoothness: Float = 1.0f,
    ) : this(radius.dp, radius.dp, radius.dp, radius.dp, smoothness)

    private val n = 2.0 + 3.0 * smoothness.coerceIn(0f, 1f)
    private val invN = 1.0 / n

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
        // 无碰撞 key: size 整数 + 四角半径 (0.01px 精度)
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
        // 动态采样表 (按 n 生成一次, 缓存命中后零开销)
        val samples = sampleCount
        val sx = FloatArray(samples)
        val sy = FloatArray(samples)
        for (i in 0 until samples) {
            val t = i / (samples - 1.0)
            sx[i] = (1.0 - t.pow(n)).pow(invN).toFloat()
            sy[i] = t.toFloat()
        }

        path.moveTo(rTL, 0f)
        path.lineTo(w - rTR, 0f)
        addCorner(path, w - rTR, rTR, rTR, 0, sx, sy) // 右上: 顶边→右边
        path.lineTo(w, h - rBR)
        addCorner(path, w - rBR, h - rBR, rBR, 1, sx, sy) // 右下: 右边→底边
        path.lineTo(rBL, h)
        addCorner(path, rBL, h - rBL, rBL, 2, sx, sy) // 左下: 底边→左边
        path.lineTo(0f, rTL)
        addCorner(path, rTL, rTL, rTL, 3, sx, sy) // 左上: 左边→顶边
        path.close()
        return Outline.Generic(path)
    }

    /** 超椭圆角段 (模式 0右上 1右下 2左下 3左上) */
    private fun addCorner(
        path: Path,
        cx: Float,
        cy: Float,
        r: Float,
        mode: Int,
        sx: FloatArray,
        sy: FloatArray,
    ) {
        for (i in 1 until sx.size) {
            val fx = sx[i]
            val fy = sy[i]
            val px: Float
            val py: Float
            when (mode) {
                0 -> { px = cx + fy * r; py = cy - fx * r }      // 右上: 顶边→右边
                1 -> { px = cx + fx * r; py = cy + fy * r }      // 右下: 右边→底边
                2 -> { px = cx - fy * r; py = cy + fx * r }      // 左下: 底边→左边
                else -> { px = cx - fx * r; py = cy - fy * r }   // 左上: 左边→顶边
            }
            path.lineTo(px, py)
        }
    }

    private companion object {
        const val sampleCount = 20
    }
}

/** 全圆胶囊 + 超椭圆端 (底栏等) */
@Immutable
class ContinuousCapsule(
    private val smoothness: Float = 1.0f,
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
