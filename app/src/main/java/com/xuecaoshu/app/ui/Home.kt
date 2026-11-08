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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.xuecaoshu.app.data.CursiveFace
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.core.Animatable
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

    // 还有没复习完的字时，点「学习」先劝一句
    var askReview by remember { mutableStateOf(0) }

    // 搜索浮层的展开状态在下面 BoxWithConstraints 里（需要屏高）

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val fullH = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }

    // off: 0 = 面板完全展开，-fullH = 完全收起。
    // 拖动时同步改这个 state（不走协程，才跟得上手指），松手再做吸附动画。
    var off by remember { mutableFloatStateOf(-1f) }
    var panelOpen by remember { mutableStateOf(false) }
    var settledOpen by remember { mutableStateOf(false) }

    LaunchedEffect(fullH) { if (off == -1f) off = -fullH }

    val settleTo: (Float) -> Unit = { t ->
        scope.launch {
            androidx.compose.animation.core.animate(
                initialValue = off,
                targetValue = t,
                animationSpec = androidx.compose.animation.core.tween(200)
            ) { v, _ -> off = v }
            if (t >= -1f) {
                settledOpen = true
            } else {
                panelOpen = false
                settledOpen = false
            }
        }
    }

    BackHandler(enabled = panelOpen) { settleTo(-fullH) }

    val onDrag: (Float) -> Unit = { d ->
        if (!panelOpen) {
            if (d > 0f) {
                panelOpen = true
                off = (-fullH + d).coerceIn(-fullH, 0f)
            }
        } else {
            off = (off + d).coerceIn(-fullH, 0f)
        }
    }
    val onDragEnd: (Float) -> Unit = { v ->
        // 快滑看方向（两个方向同样灵敏），慢滑看拉过没过半
        val open = if (kotlin.math.abs(v) > 800f) v > 0f
        else off > -fullH * 0.5f
        settleTo(if (open) 0f else -fullH)
    }

    Column(
        Modifier
            .fillMaxSize()
            .draggable(
                orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                state = rememberDraggableState { onDrag(it) },
                onDragStopped = { onDragEnd(it) }
            )
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
            ) {
                val pending = AppState.pendingReviewCount()
                if (pending > 0) askReview = pending else onStartStudy(StudyMode.LEARN)
            }
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

    if (askReview > 0) {
        AlertDialog(
            onDismissRequest = { askReview = 0 },
            containerColor = p.card,
            titleContentColor = p.ink,
            textContentColor = p.ink2,
            title = { Text("先复习一下？", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "还有 $askReview 个字等你来复习哦，建议先复习再学习",
                    fontSize = 14.sp, lineHeight = 22.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askReview = 0
                    onStartStudy(StudyMode.REVIEW)
                }) {
                    Text("马上复习", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    askReview = 0
                    onStartStudy(StudyMode.LEARN)
                }) {
                    Text("仍然学习", color = p.ink2, fontSize = 14.sp)
                }
            }
        )
    }

    // 搜索浮层：盖在主页上面，上滑或返回关闭
    if (panelOpen) {
        // 从顶部「拉开」：面板按整屏尺寸铺好，外层用一个高度=揭幕高度的框把它裁掉，
        // 所以拖动时露出来的始终是面板的最上面一截，跟手。
        val reveal = (fullH + off).coerceIn(0f, fullH)
        val revealDp = with(androidx.compose.ui.platform.LocalDensity.current) {
            reveal.toDp()
        }
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(revealDp)
                    .clipToBounds()
            ) {
                SearchPanel(
                    active = settledOpen,
                    onClose = { settleTo(-fullH) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(androidx.compose.ui.platform.LocalDensity.current) {
                            fullH.toDp()
                        })
                        .draggable(
                            orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                            state = rememberDraggableState { onDrag(it) },
                            onDragStopped = { onDragEnd(it) }
                        )
                )
            }
        }
    }
    }
}

/** 按中文标点断句，丢掉标点本身。 */
private val PUNCT = charArrayOf(
    '，', '。', '、', '；', '：', '！', '？', '「', '」', '『', '』',
    '（', '）', '《', '》', '…', '—', ',', '.', '!', '?', ';', ':', '\n', ' '
)

private fun splitQuote(text: String): List<String> {
    val parts = text.split(*PUNCT).map { it.trim() }.filter { it.isNotEmpty() }
    return if (parts.isEmpty()) listOf(text) else parts
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


/**
 * 搜索面板：从主页往下拉出来的那一层。
 * 顶部搜索框（自动聚焦弹输入法），下面三个草书字体按钮，
 * 再下面每个字一行：楷书、繁体、草书、草书繁体。
 */
@Composable
private fun SearchPanel(
    active: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = Ink.palette
    var q by remember { mutableStateOf("") }
    var face by remember { mutableStateOf(AppState.settings.cursiveFace.ordinal) }
    val focus = remember { FocusRequester() }

    // 完全展开后才聚焦，拖动过程中不弹键盘
    LaunchedEffect(active) {
        if (active) {
            kotlinx.coroutines.delay(60)
            runCatching { focus.requestFocus() }
        }
    }

    val chars = remember(q) {
        q.filter { it.code > 0x2E80 && !it.isWhitespace() }.take(40).map { it.toString() }
    }

    Column(
        modifier
            .background(p.bg)
            .imePadding()
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(28.dp))

        // 搜索框
        Box(
            Modifier.fillMaxWidth().height(46.dp)
                .clip(RoundedCornerShape(14.dp)).background(p.card)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (q.isEmpty()) {
                Text("输入要查的字", color = p.muted, fontSize = 15.sp)
            }
            BasicTextField(
                value = q,
                onValueChange = { q = it.take(40) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = p.ink, fontSize = 17.sp
                ),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        }
        Spacer(Modifier.height(10.dp))

        // 三个草书字体
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(0 to "行云", 1 to "钟齐", 2 to "毛泽东").forEach { (i, name) ->
                val on = face == i
                Box(
                    Modifier.padding(end = 8.dp).height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) p.ink else p.card)
                        // 只切搜索结果的显示字体，不改全局设置
                        .clickable { face = i }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name, color = if (on) onColor(p.ink) else p.ink2, fontSize = 13.sp
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        if (chars.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                Text(
                    "输入汉字，四个写法一起看\n上滑或返回可以回到主页",
                    color = p.muted, fontSize = 12.sp,
                    textAlign = TextAlign.Center, lineHeight = 20.sp
                )
            }
        } else {
            // 表头
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                listOf("楷书", "繁体", "草书", "草书繁体").forEach {
                    Text(
                        it, color = p.muted, fontSize = 10.sp,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                    )
                }
            }
            Hairline(p.line)
            Column(
                Modifier.fillMaxWidth().weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                chars.forEach { ch ->
                    SearchRow(ch, face)
                    Hairline(p.line.copy(alpha = 0.6f))
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun SearchRow(ch: String, face: Int) {
    val p = Ink.palette
    val trad = AppState.traditionalOf(ch)
    val tradFace = AppState.tradFaceOf(ch)

    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 楷书
        Text(
            ch, color = p.ink, fontSize = 34.sp,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center
        )
        // 繁体
        Text(
            trad ?: "—", color = if (trad != null) p.ink else p.muted, fontSize = 34.sp,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center
        )
        // 草书
        Text(
            ch, color = p.ink, fontSize = 40.sp,
            fontFamily = cursiveFamily(face), lineHeight = 46.sp,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center
        )
        // 草书繁体
        Text(
            if (trad != null && tradFace >= 0) trad else "—",
            color = if (trad != null && tradFace >= 0) p.ink else p.muted,
            fontSize = 40.sp, lineHeight = 46.sp,
            fontFamily = if (trad != null && tradFace >= 0) cursiveFamily(tradFace) else null,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center
        )
    }
}
