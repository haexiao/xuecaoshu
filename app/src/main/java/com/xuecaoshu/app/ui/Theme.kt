package com.xuecaoshu.app.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.xuecaoshu.app.R
import com.xuecaoshu.app.data.RegularFont

val XingyunFamily = FontFamily(Font(R.font.xingyun_caoshu))
val ZhongqiFamily = FontFamily(Font(R.font.zhongqi_caoshu))
val MaotiFamily = FontFamily(Font(R.font.maoti))
val WenkaiFamily = FontFamily(Font(R.font.lxgw_wenkai))
val NotoSansFamily = FontFamily(Font(R.font.noto_sans_hans))

data class Palette(
    val bg: Color,
    val card: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val accent: Color,
    val accentSoft: Color,
    val line: Color,
    val good: Color,
    val goodSoft: Color
)

val LightPalette = Palette(
    bg = Color(0xFFF7F3EA),
    card = Color(0xFFFDFBF6),
    ink = Color(0xFF1E1B18),
    ink2 = Color(0xFF5A5348),
    muted = Color(0xFF8C8475),
    accent = Color(0xFFA6382E),
    accentSoft = Color(0xFFF1DFDA),
    line = Color(0xFFD9D0C1),
    good = Color(0xFF3E6B5A),
    goodSoft = Color(0xFFDFEBE4)
)

val DarkPalette = Palette(
    bg = Color(0xFF14120F),
    card = Color(0xFF1F1C18),
    ink = Color(0xFFF2ECE0),
    ink2 = Color(0xFFC3B9A8),
    muted = Color(0xFF8E8577),
    accent = Color(0xFFC86A5C),
    accentSoft = Color(0xFF3A2522),
    line = Color(0xFF37322B),
    good = Color(0xFF7FB39C),
    goodSoft = Color(0xFF20302A)
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun XuecaoshuTheme(
    dark: Boolean,
    regularFont: RegularFont = RegularFont.SYSTEM,
    content: @Composable () -> Unit
) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, background = p.bg, surface = p.card,
            surfaceVariant = p.line, surfaceContainer = p.card,
            surfaceContainerHigh = p.card, surfaceContainerHighest = p.card,
            onPrimary = Color.White, onBackground = p.ink, onSurface = p.ink,
            onSurfaceVariant = p.ink2
        )
    } else {
        lightColorScheme(
            primary = p.accent, background = p.bg, surface = p.card,
            surfaceVariant = p.line, surfaceContainer = p.card,
            surfaceContainerHigh = p.card, surfaceContainerHighest = p.card,
            onPrimary = Color.White, onBackground = p.ink, onSurface = p.ink,
            onSurfaceVariant = p.ink2
        )
    }
    val family = when (regularFont) {
        RegularFont.WENKAI -> WenkaiFamily
        RegularFont.NOTO -> NotoSansFamily
        else -> FontFamily.Default
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = family)
            ) {
                content()
            }
        }
    }
}

/** 给定背景色，返回在其上可读的前景色（深色模式下同样成立）。 */
fun onColor(background: Color): Color =
    if (background.luminance() > 0.42f) Color(0xFF1E1B18) else Color.White

object Ink {
    val palette: Palette
        @Composable get() = LocalPalette.current
}
