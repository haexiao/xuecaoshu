package com.xuecaoshu.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuecaoshu.app.AppState
import com.xuecaoshu.app.Feedback
import com.xuecaoshu.app.data.QuoteApi
import com.xuecaoshu.app.data.StudyMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onStartStudy: (StudyMode) -> Unit,
    onOpenBook: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val p = Ink.palette
    val ctx = LocalContext.current
    // 冷启动先用缓存（没有缓存才用内置句子），随后后台静默刷新
    var quote by remember { mutableStateOf(QuoteApi.loadCache(ctx) ?: QuoteApi.fallback()) }
    var quoteCursive by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 一言跟随「我的 → 草书字体」里选中的那一款
    val faceFamily: FontFamily = cursiveFamily(AppState.settings.cursiveFace.ordinal)

    val load: () -> Unit = {
        if (!loading) {
            loading = true
            scope.launch {
                val remote = withContext(Dispatchers.IO) { QuoteApi.fetchBlocking() }
                if (remote != null) {
                    quote = remote
                    QuoteApi.saveCache(ctx, remote)
                    offline = false
                } else {
                    offline = true
                }
                loading = false
            }
        }
    }

    // 首次进入主页
    LaunchedEffect(Unit) { load() }
    // 从后台回到前台也换一条
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { load() }

    val unlearned = AppState.unlearnedCount()
    val due = AppState.dueCount()

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .padding(horizontal = 26.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            "／  一 言  ／",
            color = p.muted,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(6.dp))

        // 竖排：字号固定，一列排满就换到左边一列（自右向左）
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.TopEnd
        ) {
            val qSize = AppState.settings.quoteSize.toFloat()   // 字号固定，字数多了换列
            val lineH = qSize * 1.16f             // 每个字占的高度
            val perCol = ((maxHeight.value - 8f) / lineH).toInt().coerceAtLeast(1)

            // 按标点断句：每一句自己占一列；某句一列放不下，才折到下一列
            val cols = ArrayList<String>()
            splitQuote(quote.text).forEach { seg ->
                var i = 0
                while (i < seg.length) {
                    val take = minOf(perCol, seg.length - i)
                    cols.add(seg.substring(i, i + take))
                    i += take
                }
            }

            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { quoteCursive = !quoteCursive }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    // Row 从左到右排，所以倒过来放，第一列才在最右边
                    cols.asReversed().forEach { line ->
                        Spacer(Modifier.width(if (cols.size > 1) 6.dp else 0.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            line.forEach { ch ->
                                Text(
                                    text = ch.toString(),
                                    fontSize = qSize.sp,
                                    color = p.ink,
                                    fontFamily = if (quoteCursive) faceFamily
                                    else FontFamily.Default,
                                    lineHeight = lineH.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // 出处 + 操作
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (quote.from.isNotBlank()) {
                Text("——《${quote.from}》", color = p.muted, fontSize = 9.sp)
                Spacer(Modifier.padding(horizontal = 5.dp))
            }
            Text(
                "换一句",
                color = if (loading) p.muted else p.accent,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !loading) {
                        Feedback.tap()
                        quoteCursive = true
                        load()
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            if (offline) {
                Spacer(Modifier.padding(horizontal = 5.dp))
                Text("网络不佳", color = p.muted, fontSize = 9.sp)
            }
            Spacer(Modifier.padding(horizontal = 5.dp))
            Text(
                if (quoteCursive) "切换楷书" else "切换草书",
                color = p.muted,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        Feedback.tap()
                        quoteCursive = !quoteCursive
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EntryButton(
                Modifier.weight(1f), "学习", unlearned, "字待学",
                enabled = unlearned > 0
            ) { onStartStudy(StudyMode.LEARN) }
            EntryButton(
                Modifier.weight(1f), "复习", due, "字到期",
                enabled = due > 0
            ) { onStartStudy(StudyMode.REVIEW) }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TabButton(Modifier.weight(1f), "字库", true) { onOpenBook() }
            TabButton(Modifier.weight(1f), "我的", false) { onOpenSettings() }
        }

        Spacer(Modifier.height(14.dp))
    }
}

/** 按中文标点断句，丢掉标点本身。 */
private val PUNCT = charArrayOf(
    '，', '。', '、', '；', '：', '！', '？', '「', '」', '『', '』',
    '（', '）', '《', '》', '…', '—', ',', '.', '!', '?', ';', ':', '\n', ' '
)

private fun splitQuote(text: String): List<String> {
    val parts = text.split(*PUNCT).map { it.trim() }.filter { it.isNotEmpty() }
    return if (parts.isEmpty()) listOf(text) else parts.take(4)
}

/** 按字数挑一个能塞进竖排区域的字号。 */
@Composable
private fun EntryButton(
    modifier: Modifier,
    title: String,
    count: Int,
    unit: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val p = Ink.palette
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) p.card else p.card.copy(alpha = 0.5f))
            .clickable(enabled = enabled) {
                Feedback.tap()
                onClick()
            }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(
            count.toString(),
            color = if (enabled) p.accent else p.muted,
            fontSize = 38.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(1.dp))
        Text(unit, color = p.muted, fontSize = 11.sp)
    }
}

@Composable
private fun TabButton(
    modifier: Modifier,
    text: String,
    primary: Boolean,
    onClick: () -> Unit
) {
    val p = Ink.palette
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) p.goodSoft else p.card)
            .clickable {
                Feedback.tap()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (primary) p.good else p.ink2,
            fontSize = 14.sp,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
