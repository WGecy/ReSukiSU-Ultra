package com.tesla.resukisuultra.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 自研 G3 连续曲率圆角 (对标 iOS 五次超椭圆观感)
 *
 * 每个角用 3 段三次贝塞尔曲线逼近超椭圆 (每段 30°):
 * 曲率从直边(0) 到角顶点平滑渐进, 直线/圆角衔接处无视觉突变。
 *
 * @param smoothness 润度: 0 = 标准圆, 1 = 方润超椭圆, 默认 0.7 对标 iOS G3
 */
@Immutable
class ContinuousCornerShape(
    private val topStart: Dp = 0.dp,
    private val topEnd: Dp = topStart,
    private val bottomEnd: Dp = topEnd,
    private val bottomStart: Dp = topStart,
    private val smoothness: Float = 0.5f,
) : Shape {

    /** 像素 Float 构造 (兼容 RoundedCornerShape(Float) 用法) */
    constructor(
        radius: Float,
        smoothness: Float = 0.7f,
    ) : this(radius.dp, radius.dp, radius.dp, radius.dp, smoothness)

    /** 像素 Int 构造 (兼容 RoundedCornerShape(Int) 用法) */
    constructor(
        radius: Int,
        smoothness: Float = 0.7f,
    ) : this(radius.dp, radius.dp, radius.dp, radius.dp, smoothness)

    // 30° 弧贝塞尔系数 k = 4/3*tan(7.5°) ≈ 0.1754; G3 超椭圆内收系数 ≈ 0.149
    private val k30 = 0.1754f + (0.149f - 0.1754f) * smoothness.coerceIn(0f, 1f)

    // Path 缓存: (size, radius) 不变时复用 (静止界面零重复计算)
    @Volatile
    private var cacheKey: String? = null
    @Volatile
    private var cacheOutline: Outline? = null

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val key = "${size.width.toInt()}x${size.height.toInt()}|" +
            "${topStart.value.toInt()},${topEnd.value.toInt()}," +
            "${bottomEnd.value.toInt()},${bottomStart.value.toInt()}"
        cacheKey?.let { if (it == key) return cacheOutline!! }

        val outline = buildOutline(size, layoutDirection, density)
        cacheKey = key
        cacheOutline = outline
        return outline
    }

    private fun buildOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }
        val half = min(size.width, size.height) * 0.5f
        val rTL = with(density) { topStart.toPx() }.coerceIn(0f, half)
        val rTR = with(density) { topEnd.toPx() }.coerceIn(0f, half)
        val rBR = with(density) { bottomEnd.toPx() }.coerceIn(0f, half)
        val rBL = with(density) { bottomStart.toPx() }.coerceIn(0f, half)
        if (rTL == 0f && rTR == 0f && rBR == 0f && rBL == 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }

        val w = size.width
        val h = size.height
        val path = Path()
        path.moveTo(rTL, 0f)
        path.lineTo(w - rTR, 0f)
        addCorner(path, w - rTR, rTR, rTR, 0f, -1f, 1f, 0f) // 右上: 顶边→右边
        path.lineTo(w, h - rBR)
        addCorner(path, w - rBR, h - rBR, rBR, 1f, 0f, 0f, 1f) // 右下: 右边→底边
        path.lineTo(rBL, h)
        addCorner(path, rBL, h - rBL, rBL, 0f, 1f, -1f, 0f) // 左下: 底边→左边
        path.lineTo(0f, rTL)
        addCorner(path, rTL, rTL, rTL, -1f, 0f, 0f, -1f) // 左上: 左边→顶边
        path.close()
        return Outline.Generic(path)
    }

    /**
     * 单个角: 3 段 30° 贝塞尔 (曲率连续过渡)
     * 方向 dirIn(0°) → dirOut(90°), 中间 30°/60° 点由旋转给出
     */
    private fun addCorner(
        path: Path,
        cx: Float,
        cy: Float,
        r: Float,
        dxIn: Float,
        dyIn: Float,
        dxOut: Float,
        dyOut: Float,
    ) {
        // 中间方向 (旋转 30° / 60°)
        val c30 = 0.8660254f
        val s30 = 0.5f
        val c60 = 0.5f
        val s60 = 0.8660254f
        val m1x = dxIn * c30 - dyIn * s30
        val m1y = dxIn * s30 + dyIn * c30
        val m2x = dxIn * c60 - dyIn * s60
        val m2y = dxIn * s60 + dyIn * c60

        // 5 个点: A(0°) → M1(30°) → M2(60°) → B(90°)
        val a0x = cx + dxIn * r
        val a0y = cy + dyIn * r
        val a1x = cx + m1x * r
        val a1y = cy + m1y * r
        val a2x = cx + m2x * r
        val a2y = cy + m2y * r
        val a3x = cx + dxOut * r
        val a3y = cy + dyOut * r

        // 每段切线 = 逆时针旋转(该段起点方向): (x,y) → (-y,x)
        bezierSeg(path, a0x, a0y, -dyIn, dxIn, a1x, a1y, -m1y, m1x, r, isFirst = true)
        bezierSeg(path, a1x, a1y, -m1y, m1x, a2x, a2y, -m2y, m2x, r)
        bezierSeg(path, a2x, a2y, -m2y, m2x, a3x, a3y, -dyOut, dxOut, r, isLast = true)
    }

    /** 一段 30° 贝塞尔: P0 → P3, 控制点 = 两端切线方向偏移 (首段起点缓入) */
    private fun bezierSeg(
        path: Path,
        p0x: Float, p0y: Float, t0x: Float, t0y: Float,
        p3x: Float, p3y: Float, t3x: Float, t3y: Float,
        r: Float,
        isFirst: Boolean = false,
        isLast: Boolean = false,
    ) {
        // 首段起点与末段终点: 控制点距离 0.75× (曲率从直线渐变 — 衔接处更顺)
        val kStart = if (isFirst) k30 * 0.75f else k30
        val kEnd = if (isLast) k30 * 0.75f else k30
        path.cubicTo(
            p0x + t0x * r * kStart, p0y + t0y * r * kStart,
            p3x - t3x * r * kEnd, p3y - t3y * r * kEnd,
            p3x, p3y,
        )
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
        val key = "${size.width.toInt()}x${size.height.toInt()}"
        cacheKey?.let { if (it == key) return cacheOutline!! }
        val r = min(size.width, size.height) * 0.5f
        val shape = ContinuousCornerShape(r.dp, r.dp, r.dp, r.dp, smoothness)
        val outline = shape.createOutline(size, layoutDirection, density)
        cacheKey = key
        cacheOutline = outline
        return outline
    }
}
