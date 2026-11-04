package com.xuecaoshu.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import com.xuecaoshu.app.STREAK_GOAL
import com.xuecaoshu.app.data.StudyMode
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuecaoshu.app.AppState
import com.xuecaoshu.app.Feedback
import com.xuecaoshu.app.Card
import com.xuecaoshu.app.StudySession

@Composable
fun StudyScreen(onExit: () -> Unit) {
    val s = AppState.session ?: return

    BackHandler {
        AppState.endSession()
        onExit()
    }

    val card = s.current
    if (card == null) {
        FinishScreen(s, onExit)
        return
    }

    StudyBody(card, onExit)
}

@Composable
private fun StudyBody(card: Card, onExit: () -> Unit) {
    val p = Ink.palette
    val s = AppState.session ?: return

    val faceFont = cursiveFamily(card.font)
    val promptCursive = card.promptIsCursive
    val answered = s.chosenIndex != -1
    val isRight = s.chosenIndex == card.correctIndex
    val trad = AppState.traditionalOf(card.entry.char)

    val inspect = when {
        s.inspectIndex < 0 -> -1
        s.inspectIndex == card.correctIndex -> -1
        else -> s.inspectIndex
    }
    val inspectChar = if (inspect in card.options.indices) card.options[inspect] else null

    Column(Modifier.fillMaxSize().background(p.bg).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹", color = p.ink2, fontSize = 26.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .clickable {
                        Feedback.tap()
                        AppState.endSession()
                        onExit()
                    }
                    .padding(horizontal = 8.dp)
            )
            Text(
                "${s.done.coerceIn(0, s.total)} / ${s.total}",
                color = p.muted, fontSize = 13.sp
            )
            Spacer(Modifier.weight(1f))
            val starred = AppState.progressOf(card.entry.char).starred
            Text(
                if (starred) "★ 已收藏" else "☆ 收藏",
                color = if (starred) p.accent else p.muted,
                fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(14.dp))
                    .clickable {
                        Feedback.tap()
                        AppState.toggleStar()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Spacer(Modifier.size(6.dp))
            Box(
                Modifier.clip(RoundedCornerShape(14.dp)).background(p.good)
                    .clickable {
                        Feedback.tap()
                        AppState.markKnown()
                    }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text("熟", color = onColor(p.good), fontSize = 12.sp)
            }
        }

        if (s.mode == StudyMode.LEARN) {
            val streak = AppState.progressOf(card.entry.char).f1
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until STREAK_GOAL) {
                    Box(
                        Modifier.padding(horizontal = 4.dp).width(46.dp).height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i < streak) p.good else p.line)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            Modifier.fillMaxWidth().weight(0.86f).clip(RoundedCornerShape(18.dp))
                .background(p.card),
            contentAlignment = Alignment.Center
        ) {
            // 只有这个字有换卡动画：干脆的交叉淡入淡出，不做缩放
            AnimatedContent(
                targetState = card,
                transitionSpec = {
                    fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(140))
                },
                label = "glyph"
            ) { c ->
                Glyph(
                    text = c.entry.char,
                    cursive = c.promptIsCursive,
                    size = if (c.promptIsCursive) 176.sp else 152.sp,
                    color = p.ink,
                    family = cursiveFamily(c.font)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in 0 until 2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (col in 0 until 2) {
                        val idx = row * 2 + col
                        OptionCell(
                            modifier = Modifier.weight(1f),
                            text = card.options.getOrNull(idx) ?: "",
                            showCursive = !promptCursive,
                            face = faceFont,
                            state = optionState(idx, card, s.chosenIndex, s.inspectIndex, answered)
                        ) {
                            Feedback.tap()
                            if (!answered) AppState.answer(idx) else AppState.inspect(idx)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 当前这张卡要显示的解析内容：答对/答错那一刻就已经有了，
        // 所以展开动画每张卡都会正常播。退场动画期间沿用上一份。
        val panelNow = if (answered) {
            PanelData(
                char = card.entry.char,
                trad = trad,
                tradFace = AppState.tradFaceOf(card.entry.char),
                inspectChar = inspectChar,
                promptCursive = promptCursive,
                faceFont = faceFont
            )
        } else null
        var lastPanel by remember { mutableStateOf<PanelData?>(null) }
        if (panelNow != null) lastPanel = panelNow

        AnimatedVisibility(
            visible = answered,
            enter = fadeIn(tween(160)) + expandVertically(tween(240)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(240))
        ) {
            val d = panelNow ?: lastPanel ?: return@AnimatedVisibility
            Column {
                AnswerPanel(d.char, d.trad, d.tradFace, d.faceFont)
                if (d.inspectChar != null) {
                    Spacer(Modifier.height(8.dp))
                    OtherFormCard(d.inspectChar, d.promptCursive, d.faceFont)
                }
            }
        }

        Spacer(Modifier.weight(0.05f))

        val label = if (answered) "下一字" else "看答案"
        val bg = if (answered) p.good else p.ink
        Box(
            Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(29.dp))
                .background(bg)
                .clickable {
                    Feedback.tap()
                    if (answered) AppState.advance() else AppState.reveal()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = onColor(bg), fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(12.dp))
    }
}

/** 解析面板要显示的内容，退场动画期间沿用上一份。 */
private data class PanelData(
    val char: String,
    val trad: String?,
    val tradFace: Int,
    val inspectChar: String?,
    val promptCursive: Boolean,
    val faceFont: FontFamily
)

/** 0=默认 1=正确(绿) 2=选错(红) 3=正在查看 4=变暗 */
private fun optionState(
    idx: Int, card: Card, chosen: Int, inspect: Int, answered: Boolean
): Int {
    if (!answered) return 0
    if (idx == card.correctIndex) return 1
    if (idx == chosen) return 2
    if (idx == inspect) return 3
    return 4
}

@Composable
private fun OptionCell(
    modifier: Modifier,
    text: String,
    showCursive: Boolean,
    face: FontFamily,
    state: Int,
    onClick: () -> Unit
) {
    val p = Ink.palette
    val bg: Color
    val border: Color
    val width: androidx.compose.ui.unit.Dp
    when (state) {
        1 -> { bg = p.goodSoft; border = p.good; width = 1.8.dp }
        2 -> { bg = p.accentSoft; border = p.accent; width = 1.8.dp }
        3 -> { bg = p.card; border = p.ink2; width = 1.5.dp }
        4 -> { bg = p.card.copy(alpha = 0.55f); border = p.line; width = 1.dp }
        else -> { bg = p.card; border = p.line; width = 1.dp }
    }
    Box(
        modifier.height(104.dp).clip(RoundedCornerShape(14.dp)).background(bg)
            .border(width, border, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Glyph(
            text = text,
            cursive = showCursive,
            size = if (showCursive) 62.sp else 58.sp,
            color = if (state == 4) p.muted else p.ink,
            family = face
        )
    }
}

/** 正确答案：草书 / 楷书 / 繁体 / 繁体草书 */
@Composable
private fun AnswerPanel(ch: String, trad: String?, tradFace: Int, face: FontFamily) {
    val p = Ink.palette
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.card)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            if (trad != null) "正确答案　「$ch」　繁体「$trad」" else "正确答案　「$ch」",
            color = p.accent, fontSize = 11.sp
        )
        Spacer(Modifier.height(2.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FormColumn(ch, "草书", true, face)
            FormColumn(ch, "楷书", false, null)
            if (trad != null) {
                FormColumn(trad, "繁体", false, null)
                // 三种草书字体都没有这个字形时不画繁草，免得显示成方框
                if (tradFace >= 0) FormColumn(trad, "繁草", true, cursiveFamily(tradFace))
            }
        }
    }
}

@Composable
private fun FormColumn(text: String, label: String, cursive: Boolean, face: FontFamily?) {
    val p = Ink.palette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Glyph(text, cursive, 46.sp, p.ink, face ?: FontFamily.Default)
        Spacer(Modifier.height(2.dp))
        Text(label, color = p.muted, fontSize = 9.sp)
    }
}

/** 其他字的另一种写法 */
@Composable
private fun OtherFormCard(ch: String, promptCursive: Boolean, face: FontFamily) {
    val p = Ink.palette
    val otherTrad = AppState.traditionalOf(ch)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.card)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (promptCursive) "「$ch」的草书写法" else "「$ch」的楷书写法",
                color = p.ink2, fontSize = 11.sp
            )
            if (otherTrad != null) {
                Text("繁体「$otherTrad」", color = p.muted, fontSize = 10.sp)
            }
        }
        Glyph(
            text = ch,
            cursive = promptCursive,
            size = 52.sp,
            color = p.ink,
            family = face
        )
        if (otherTrad != null) {
            Spacer(Modifier.size(14.dp))
            Glyph(
                otherTrad, promptCursive, 46.sp, p.accent,
                if (promptCursive && AppState.tradFaceOf(ch) >= 0)
                    cursiveFamily(AppState.tradFaceOf(ch))
                else FontFamily.Default
            )
        }
    }
}

private val PRAISES = listOf(
    "字字有神", "落笔生风", "渐入佳境", "一气呵成",
    "下笔有由", "笔笔生花", "行云流水", "铁画银钩",
    "渐通草法", "一日千里"
)

private var lastPraise: String? = null

private fun nextPraise(): String {
    val pool = PRAISES.filter { it != lastPraise }
    val pick = pool.random()
    lastPraise = pick
    return pick
}

@Composable
private fun FinishScreen(s: StudySession, onExit: () -> Unit) {
    val p = Ink.palette

    val pop = remember { Animatable(0.55f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch {
            pop.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow)
            )
        }
        fade.animateTo(1f, tween(520))
    }

    // 每次结算换一句，且不和上一句重复
    val praise = remember(s) { nextPraise() }
    val rate = if (s.answeredCount == 0) 0
    else (s.firstTryCorrect * 100 / s.answeredCount).coerceIn(0, 100)

    Column(
        Modifier.fillMaxSize().background(p.bg).padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 大字入场
        Box(
            Modifier.fillMaxWidth().height(190.dp),
            contentAlignment = Alignment.Center
        ) {
            Glyph(
                text = "成",
                cursive = true,
                size = 150.sp,
                color = p.ink.copy(alpha = fade.value),
                family = cursiveFamily(AppState.settings.cursiveFace.ordinal)
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            if (s.mode == StudyMode.REVIEW) "这一轮复习完了" else "这一组学完了",
            color = p.ink, fontSize = 23.sp, fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        Text(praise, color = p.accent, fontSize = 14.sp)
        Spacer(Modifier.height(26.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("本组", "${s.total}", "字", p)
            StatBox("答对", "${s.firstTryCorrect}/${s.answeredCount}", "次", p)
            StatBox("正确率", "$rate", "%", p)
        }

        Spacer(Modifier.height(34.dp))

        Box(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp))
                .background(p.ink)
                .clickable {
                    Feedback.tap()
                    AppState.endSession()
                    onExit()
                },
            contentAlignment = Alignment.Center
        ) {
            Text("回到主页", color = onColor(p.ink), fontSize = 15.sp)
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, unit: String, p: Palette) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(p.card)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = p.muted, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = p.accent, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text(unit, color = p.muted, fontSize = 10.sp)
        }
    }
}
