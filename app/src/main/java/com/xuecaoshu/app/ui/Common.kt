package com.xuecaoshu.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuecaoshu.app.data.DayStat

/** A single character drawn either in a cursive face or in the regular font. */
/** 草书字体：0 = 行云，1 = 钟齐，2 = 毛体。全 App 只有这一处映射。 */
fun cursiveFamily(face: Int): FontFamily = when (face) {
    1 -> ZhongqiFamily
    2 -> MaotiFamily
    else -> XingyunFamily
}

@Composable
fun Glyph(
    text: String,
    cursive: Boolean,
    size: TextUnit,
    color: Color,
    family: FontFamily? = null
) {
    Text(
        text = text,
        color = color,
        fontSize = size,
        fontFamily = if (!cursive) null else (family ?: XingyunFamily),
        fontWeight = if (cursive) FontWeight.Normal else FontWeight.Light,
        textAlign = TextAlign.Center,
        lineHeight = size * 1.15f
    )
}

@Composable
fun Hairline(color: Color = Ink.palette.line, thickness: androidx.compose.ui.unit.Dp = 1.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color)
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Ink.palette.muted,
        fontSize = 12.sp,
        modifier = modifier
    )
}

@Composable
fun StatCell(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val p = Ink.palette
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(p.card)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label, color = p.muted, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(3.dp))
            Text(unit, color = p.muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

/** Weekly bar chart: 学习 in accent, 复习 in a light tone stacked below. */
@Composable
fun WeekChart(data: List<Pair<String, DayStat>>, modifier: Modifier = Modifier) {
    val p = Ink.palette
    val maxV = (data.maxOfOrNull { it.second.learned + it.second.reviewed } ?: 0).coerceAtLeast(1)
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = data.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = slot * 0.46f
            data.forEachIndexed { i, item ->
                val learned = item.second.learned
                val reviewed = item.second.reviewed
                val total = learned + reviewed
                if (total == 0) return@forEachIndexed
                val h = size.height * (total.toFloat() / maxV) * 0.86f
                val hLearn = h * (learned.toFloat() / total)
                val hRev = h - hLearn
                val x = slot * i + (slot - barW) / 2f
                // reviewed (bottom, light)
                if (hRev > 0.5f) {
                    drawRoundRect(
                        color = p.accent.copy(alpha = 0.35f),
                        topLeft = Offset(x, size.height - hRev),
                        size = Size(barW, hRev),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW * 0.28f)
                    )
                }
                if (hLearn > 0.5f) {
                    drawRoundRect(
                        color = p.accent,
                        topLeft = Offset(x, size.height - hRev - hLearn),
                        size = Size(barW, hLearn),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW * 0.28f)
                    )
                }
            }
            drawLine(
                color = p.line,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.5f
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            labels.forEach {
                Text(it, color = p.muted, fontSize = 10.sp)
            }
        }
    }
}
