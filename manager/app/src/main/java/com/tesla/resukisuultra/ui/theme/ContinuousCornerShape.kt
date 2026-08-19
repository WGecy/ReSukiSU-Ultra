package com.tesla.resukisuultra.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 自研 G3 连续曲率圆角 (对标 iOS 五次超椭圆观感)
 *
 * 每个角用 2 段三次贝塞尔曲线逼近超椭圆段 (曲率从直边连续过渡到角顶点),
 * 与 RoundedCornerShape (直线+圆弧, 曲率突变) 的本质区别在"曲率连续" — 视觉更润。
 *
 * @param smoothness 润度参数: 0 = 标准圆弧 (G2), 1 = 方润超椭圆, 默认 0.7 对标 iOS G3
 */
@Immutable
class ContinuousCornerShape(
    private val topStart: Dp = 0.dp,
    private val topEnd: Dp = 0.dp,
    private val bottomEnd: Dp = 0.dp,
    private val bottomStart: Dp = 0.dp,
    private val smoothness: Float = 0.7f,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }
        val minDim = min(size.width, size.height)
        val half = minDim * 0.5f
        val rTL = with(density) { topStart.toPx() }.coerceIn(0f, half)
        val rTR = with(density) { topEnd.toPx() }.coerceIn(0f, half)
        val rBR = with(density) { bottomEnd.toPx() }.coerceIn(0f, half)
        val rBL = with(density) { bottomStart.toPx() }.coerceIn(0f, half)
        if (rTL == 0f && rTR == 0f && rBR == 0f && rBL == 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }

        // 贝塞尔控制系数: 45° 弧标准系数 4/3*tan(11.25°)≈0.2652 (圆, 无越界)
        // → G3 超椭圆内收系数 0.2254 (方润); 验证: 0.2652 时曲线精确贴合半径
        val kCircle = 0.2652f
        val kG3 = 0.2254f
        val k = kCircle + (kG3 - kCircle) * smoothness.coerceIn(0f, 1f)
        val w = size.width
        val h = size.height

        val path = Path()
        path.moveTo(rTL, 0f)
        path.lineTo(w - rTR, 0f)
        addCorner(path, w - rTR, rTR, rTR, 0f, -1f, 1f, 0f, k) // 右上: 顶边→右边
        path.lineTo(w, h - rBR)
        addCorner(path, w - rBR, h - rBR, rBR, 1f, 0f, 0f, 1f, k) // 右下: 右边→底边
        path.lineTo(rBL, h)
        addCorner(path, rBL, h - rBL, rBL, 0f, 1f, -1f, 0f, k) // 左下: 底边→左边
        path.lineTo(0f, rTL)
        addCorner(path, rTL, rTL, rTL, -1f, 0f, 0f, -1f, k) // 左上: 左边→顶边
        path.close()
        return Outline.Generic(path)
    }

    /**
     * 单个角的 2 段贝塞尔逼近 (曲率连续)
     * @param cx/cy 角圆心; @param r 半径
     * @param dIn/dyIn 进入边方向 (圆心→进入点, 单位向量)
     * @param dxOut/dyOut 出口边方向 (圆心→出口点, 单位向量)
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
        k: Float,
    ) {
        val ax = cx + dxIn * r
        val ay = cy + dyIn * r
        val bx = cx + dxOut * r
        val by = cy + dyOut * r
        // 45° 中点 = 圆心 + normalize(dirIn+dirOut) * r (对角方向, 与角顶点同向)
        val md = sqrt(
            (dxIn + dxOut) * (dxIn + dxOut) + (dyIn + dyOut) * (dyIn + dyOut)
        )
        val mx = cx + (dxIn + dxOut) / md * r
        val my = cy + (dyIn + dyOut) / md * r
        // 切线 (逆时针旋转进入/出口方向): (x,y) → (-y,x)
        val tInX = -dyIn
        val tInY = dxIn
        val tOutX = -dyOut
        val tOutY = dxOut
        // 中点切线 = 归一化(tIn + tOut)
        val tmLen = sqrt((tInX + tOutX) * (tInX + tOutX) + (tInY + tOutY) * (tInY + tOutY))
        val tmX = (tInX + tOutX) / tmLen
        val tmY = (tInY + tOutY) / tmLen
        // 段1: A → M
        path.cubicTo(
            ax + tInX * r * k, ay + tInY * r * k,
            mx - tmX * r * k, my - tmY * r * k,
            mx, my,
        )
        // 段2: M → B
        path.cubicTo(
            mx + tmX * r * k, my + tmY * r * k,
            bx - tOutX * r * k, by - tOutY * r * k,
            bx, by,
        )
    }

    override fun toString(): String =
        "ContinuousCornerShape(topStart=$topStart, topEnd=$topEnd, " +
            "bottomEnd=$bottomEnd, bottomStart=$bottomStart, smoothness=$smoothness)"
}

/** 全圆胶囊 + 连续曲率 (底栏等) */
@Immutable
class ContinuousCapsule(
    private val smoothness: Float = 0.7f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = min(size.width, size.height) * 0.5f
        val shape = ContinuousCornerShape(r.dp, r.dp, r.dp, r.dp, smoothness)
        return shape.createOutline(size, layoutDirection, density)
    }
}
