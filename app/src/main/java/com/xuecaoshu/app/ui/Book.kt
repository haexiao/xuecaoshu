package com.xuecaoshu.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuecaoshu.app.AppState
import com.xuecaoshu.app.Feedback
import com.xuecaoshu.app.data.DayStat
import com.xuecaoshu.app.data.LearnOrder
import java.util.Calendar
import kotlin.math.ceil
import kotlin.random.Random

enum class Metric { COUNT, TIME }
enum class ChartView { WEEK, MONTH }

@Composable
fun BookScreen(onBack: () -> Unit) {
    var listMode by remember { mutableStateOf<String?>(null) }
    BackHandler {
        if (listMode != null) listMode = null else onBack()
    }
    if (listMode != null) {
        CharListScreen(onlyStarred = listMode == "star") { listMode = null }
    } else {
        BookHome(onBack = onBack, onOpenList = { listMode = it })
    }
}

// ------------------------------------------------------------------ 字库主页
@Composable
private fun BookHome(onBack: () -> Unit, onOpenList: (String) -> Unit) {
    val p = Ink.palette
    var metric by remember { mutableStateOf(Metric.COUNT) }
    var view by remember { mutableStateOf(ChartView.WEEK) }
    var selected by remember { mutableStateOf(-1) }
    // 换视图时清掉选中，避免下标越界
    LaunchedEffect(view) { selected = -1 }

    val total = AppState.charsOfLevel(AppState.settings.level).size
    val learned = AppState.learnedCount()
    val ratio = if (total == 0) 0f else learned.toFloat() / total
    val starred = AppState.starredChars().size
    val today = AppState.todayStat()
    val all = AppState.totalStat()

    val series = if (view == ChartView.WEEK) {
        AppState.daySeries(AppState.historyDays())
    } else {
        AppState.monthSeries12()
    }

    Column(
        Modifier.fillMaxSize().background(p.bg).padding(horizontal = 22.dp)
    ) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹", color = p.ink2, fontSize = 26.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() }.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.size(4.dp))
            Text("字库", color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("$learned / $total", color = p.muted, fontSize = 11.sp)
        }

        Text(AppState.levelName, color = p.muted, fontSize = 10.sp)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(p.line)
        ) {
            Box(
                Modifier.fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(p.accent)
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EntryRow("全部字库", "$total 字", Modifier.weight(1f)) { onOpenList("all") }
            EntryRow("我的收藏", "$starred 字", Modifier.weight(1f)) { onOpenList("star") }
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel("学习统计")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("今日字数", "${today.learned}", "字", Modifier.weight(1f))
            MiniStat("累计字数", "${all.learned}", "字", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("今日时长", "${ceil(today.seconds / 60.0).toInt()}", "分钟",
                Modifier.weight(1f))
            MiniStat("累计时长", "${ceil(all.seconds / 60.0).toInt()}", "分钟",
                Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("趋势")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip("字数", metric == Metric.COUNT) { metric = Metric.COUNT }
            Spacer(Modifier.size(6.dp))
            Chip("时间", metric == Metric.TIME) { metric = Metric.TIME }
            Spacer(Modifier.weight(1f))
            Chip("最近一周", view == ChartView.WEEK) { view = ChartView.WEEK }
            Spacer(Modifier.size(6.dp))
            Chip("按月查看", view == ChartView.MONTH) { view = ChartView.MONTH }
        }
        Spacer(Modifier.height(10.dp))
        TrendChart(
            series, metric, view, selected,
            modifier = Modifier.weight(1f)
        ) { selected = it }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    val p = Ink.palette
    Text(text, color = p.muted, fontSize = 11.sp)
}

@Composable
private fun EntryRow(title: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val p = Ink.palette
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(p.card)
            .clickable {
                Feedback.tap()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("›", color = p.muted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, color = p.accent, fontSize = 13.sp)
    }
}

@Composable
private fun StatCard(label: String, value: String, unit: String, modifier: Modifier) {
    val p = Ink.palette
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(p.card)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(label, color = p.muted, fontSize = 10.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = p.ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.size(4.dp))
            Text(unit, color = p.muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, unit: String, modifier: Modifier) {
    val p = Ink.palette
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(p.card)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(label, color = p.muted, fontSize = 9.sp)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.size(2.dp))
            Text(unit, color = p.muted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val p = Ink.palette
    Box(
        Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (on) p.accent else p.card)
            .clickable {
                Feedback.tap()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(text, color = if (on) onColor(p.accent) else p.ink2, fontSize = 12.sp)
    }
}

/** 新学与复习堆叠成一根柱子，点一下看具体数值。 */
@Composable
private fun TrendChart(
    series: List<Pair<String, DayStat>>,
    metric: Metric,
    view: ChartView,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    val p = Ink.palette

    // 柱子等宽：一周视图用固定宽度（可横滑），月视图平分 12 格
    val barW = if (view == ChartView.WEEK) 34.dp else 0.dp
    val scroll = rememberScrollState()
    // 一周视图默认滑到最右（最近的一天）
    LaunchedEffect(series.size, view, scroll.maxValue) {
        if (view == ChartView.WEEK) scroll.scrollTo(scroll.maxValue)
    }

    val learnedVals = series.map { it.second.learned }
    val reviewVals = series.map { it.second.reviewed }
    val timeVals = series.map { it.second.seconds }

    val maxTotal = if (metric == Metric.COUNT) {
        series.maxOfOrNull { it.second.learned + it.second.reviewed } ?: 0
    } else {
        timeVals.maxOrNull() ?: 0
    }.coerceAtLeast(1)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val areaH = maxHeight - 44.dp          // 上面一行数字 + 下面一行日期
        Row(
            Modifier
                .fillMaxSize()
                .then(
                    if (view == ChartView.WEEK)
                        Modifier.horizontalScroll(scroll)
                    else Modifier
                ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            series.forEachIndexed { i, (key, _) ->
                val on = i == selected
                val a = learnedVals[i]
                val b = reviewVals[i]
                val total = if (metric == Metric.COUNT) a + b else timeVals[i]
                val label = if (view == ChartView.WEEK) key.takeLast(5) else key.takeLast(2)
                val tip = if (metric == Metric.COUNT) "$total"
                else "${ceil(timeVals[i] / 60.0).toInt()}"

                Column(
                    Modifier
                        .then(
                            if (view == ChartView.WEEK)
                                Modifier.width(barW)
                            else Modifier.weight(1f)
                        )
                        .fillMaxHeight()
                        .pointerInput(i) {
                            detectTapGestures { onSelect(if (selected == i) -1 else i) }
                        }
                ) {
                    // 选中的柱子：数字写在柱子上面
                    Text(
                        if (on) tip else "",
                        color = if (metric == Metric.COUNT) p.ink else p.ink,
                        fontSize = 9.sp,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth().height(16.dp),
                        textAlign = TextAlign.Center
                    )

                    Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (total == 0) {
                            Box(
                                Modifier.fillMaxWidth(0.7f).height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(p.line)
                            )
                        } else if (metric == Metric.COUNT) {
                            Column(
                                Modifier.fillMaxWidth(0.72f),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                if (b > 0) Box(
                                    Modifier.fillMaxWidth()
                                        .height(areaH * (b.toFloat() / maxTotal))
                                        .clip(
                                            RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                        )
                                        .background(if (on) p.good else p.good.copy(alpha = 0.6f))
                                )
                                if (a > 0) Box(
                                    Modifier.fillMaxWidth()
                                        .height(areaH * (a.toFloat() / maxTotal))
                                        .clip(
                                            if (b > 0) RoundedCornerShape(0.dp)
                                            else RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                        )
                                        .background(if (on) p.accent else p.accent.copy(alpha = 0.6f))
                                )
                            }
                        } else {
                            Box(
                                Modifier.fillMaxWidth(0.72f)
                                    .height(
                                        (areaH * (total.toFloat() / maxTotal))
                                            .coerceAtLeast(3.dp)
                                    )
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(
                                        if (on) p.ink else p.ink.copy(alpha = 0.45f)
                                    )
                            )
                        }
                    }

                    Text(
                        label,
                        color = if (on) p.accent else p.muted,
                        fontSize = 8.sp,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth().height(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    val p = Ink.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(text, color = p.muted, fontSize = 9.sp)
    }
}

/** 周显示星期，月显示日期，年显示月份。 */

// ------------------------------------------------------------------ 字表
/** 列表里每一格需要的东西，全部提前算好，滚动时不再查 map。 */
private data class CharCell(
    val char: String,
    val index: Int,
    val learned: Boolean,
    val mastered: Boolean,
    val starred: Boolean,
    val known: Boolean,
    val rank: Int
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CharListScreen(onlyStarred: Boolean, onBack: () -> Unit) {
    val p = Ink.palette
    var filter by remember { mutableStateOf(0) }
    var order by remember { mutableStateOf(AppState.settings.order) }
    var edit by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val flipped = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()

    val version = AppState.dataVersion
    val level = AppState.settings.level

    // 只在筛选/排序/等级/进度变化时重算，滚动时不重新过滤排序
    val shown: List<CharCell> = remember(filter, order, onlyStarred, level, version) {
        val base = if (onlyStarred) AppState.starredChars()
        else AppState.charsOfLevel(level)
        val filtered = ArrayList<CharCell>(base.size)
        base.forEach { e ->
            val g = AppState.progressOf(e.char)
            val keep = when (filter) {
                1 -> !g.learned
                2 -> g.learned && !g.mastered
                3 -> g.mastered
                4 -> g.known
                else -> true
            }
            if (keep) {
                filtered.add(
                    CharCell(
                        char = e.char,
                        index = e.index,
                        learned = g.learned,
                        mastered = g.mastered,
                        starred = g.starred,
                        known = g.known,
                        rank = AppState.freq[e.char] ?: Int.MAX_VALUE
                    )
                )
            }
        }
        when (order) {
            LearnOrder.FREQ -> filtered.sortedBy { it.rank }
            LearnOrder.BOOK -> filtered.sortedBy { it.index }
            else -> filtered.shuffled(Random(filter))
        }
    }

    val rows = remember(shown) { shown.chunked(8) }

    val tabs = listOf("全部", "未学习", "复习中", "复习完成", "已标熟")
    val orderName = when (order) {
        LearnOrder.FREQ -> "字频顺序"
        LearnOrder.BOOK -> "字库顺序"
        else -> "乱序"
    }

    fun commit(action: String) {
        val chars = selected.keys.toList()
        if (chars.isEmpty()) return
        when (action) {
            "known" -> AppState.markKnownBatch(chars)
            "reset" -> AppState.resetBatch(chars)
            "star" -> AppState.starBatch(chars, true)
            "unstar" -> AppState.starBatch(chars, false)
        }
        selected.clear()
        edit = false
        Feedback.tap()
    }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        if (edit) {
            Row(
                Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "取消", color = p.ink2, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable { selected.clear(); edit = false }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    "已选 ${selected.size}", color = p.ink, fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (selected.size == shown.size) "取消全选" else "全选",
                    color = p.accent, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (selected.size == shown.size) selected.clear()
                            else shown.forEach { selected[it.char] = true }
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val actions = when (filter) {
                    1 -> listOf("known" to "标熟", "star" to "收藏")
                    4 -> listOf("reset" to "重学", "unstar" to "取消收藏")
                    2, 3 -> listOf("known" to "标熟", "reset" to "重学", "star" to "收藏")
                    else -> listOf("known" to "标熟", "reset" to "重学", "star" to "收藏")
                }
                actions.forEach { (key, label) ->
                    val on = selected.isNotEmpty()
                    Box(
                        Modifier.weight(1f).height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (on) p.ink else p.line)
                            .clickable(enabled = on) { commit(key) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label, color = if (on) onColor(p.ink) else p.muted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        } else {
            Row(
                Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "‹", color = p.ink2, fontSize = 26.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable { onBack() }.padding(horizontal = 8.dp, vertical = 2.dp)
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    if (onlyStarred) "我的收藏" else "全部字库",
                    color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                Box {
                    Text(
                        "$orderName · ${shown.size} 字 ⌄",
                        color = p.muted, fontSize = 11.sp,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            .clickable { sortOpen = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    DropdownMenu(
                        expanded = sortOpen,
                        onDismissRequest = { sortOpen = false }
                    ) {
                        listOf(
                            LearnOrder.FREQ to "字频顺序",
                            LearnOrder.BOOK to "字库顺序",
                            LearnOrder.RANDOM to "乱序"
                        ).forEach { (o, name) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        name,
                                        color = if (o == order) p.accent else p.ink,
                                        fontSize = 14.sp
                                    )
                                },
                                onClick = {
                                    Feedback.tap()
                                    order = o
                                    sortOpen = false
                                }
                            )
                        }
                    }
                }
            }

            if (!onlyStarred) {
                Row(
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEachIndexed { i, label ->
                        val on = filter == i
                        Box(
                            Modifier.clip(RoundedCornerShape(14.dp))
                                .background(if (on) p.ink else p.card)
                                .clickable {
                                    Feedback.tap()
                                    filter = i
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                label, color = if (on) onColor(p.ink) else p.ink2,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (onlyStarred) "还没有收藏的字" else "这一档还没有字",
                    color = p.muted, fontSize = 13.sp
                )
            }
        } else {
            // 一行 8 个字：LazyColumn 只有 ~470 项，比 3755 项的网格轻得多
            Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 18.dp, end = 26.dp, top = 8.dp, bottom = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(rows, key = { r -> r.first().index }) { rowCells ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        rowCells.forEach { entry ->
                            val picked = selected.containsKey(entry.char)
                            val cursive = flipped[entry.char] ?: entry.learned
                            Box(
                                Modifier.weight(1f).height(42.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            picked -> p.ink.copy(alpha = 0.18f)
                                            entry.mastered -> p.goodSoft
                                            entry.learned -> p.card
                                            else -> Color.Transparent
                                        }
                                    )
                                    .then(
                                        if (picked) Modifier.border(
                                            1.5.dp, p.accent, RoundedCornerShape(6.dp)
                                        ) else Modifier
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (edit) {
                                                if (picked) selected.remove(entry.char)
                                                else selected[entry.char] = true
                                            } else {
                                                Feedback.tap()
                                                flipped[entry.char] = !cursive
                                            }
                                        },
                                        onLongClick = {
                                            Feedback.tap()
                                            edit = true
                                            selected[entry.char] = true
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Glyph(
                                    text = entry.char,
                                    cursive = cursive,
                                    size = 22.sp,
                                    color = when {
                                        picked -> p.ink
                                        entry.starred -> p.accent
                                        entry.mastered -> p.good
                                        entry.learned -> p.ink
                                        else -> p.muted.copy(alpha = 0.7f)
                                    }
                                )
                            }
                        }
                        if (rowCells.size < 8) {
                            repeat(8 - rowCells.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            FastScroller(listState, rows.size, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

/** 右侧拖动条：按住拖可以把 3755 个字快速拉到任意位置。 */
@Composable
private fun FastScroller(state: LazyListState, count: Int, modifier: Modifier = Modifier) {
    if (count <= 20) return
    val p = Ink.palette
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier.width(26.dp).fillMaxHeight()) {
        val trackH = maxHeight
        val thumbH = (trackH * 0.16f).coerceAtLeast(40.dp)
        val first = state.firstVisibleItemIndex
        val frac = if (count <= 1) 0f else (first.toFloat() / (count - 1)).coerceIn(0f, 1f)
        val thumbTop = (trackH - thumbH) * frac

        // 轨道
        Box(
            Modifier.align(Alignment.Center).width(3.dp).fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(p.line.copy(alpha = 0.5f))
        )
        Box(
            Modifier.align(Alignment.TopCenter)
                .offset(y = thumbTop)
                .width(6.dp).height(thumbH)
                .clip(RoundedCornerShape(3.dp))
                .background(p.muted.copy(alpha = 0.55f))
        )
        // 整条轨道都能拖
        Box(
            Modifier.fillMaxSize().pointerInput(count) {
                fun jump(y: Float) {
                    val f = (y / size.height).coerceIn(0f, 1f)
                    scope.launch {
                        state.scrollToItem((f * (count - 1)).toInt().coerceIn(0, count - 1))
                    }
                }
                detectTapGestures { jump(it.y) }
            }
            .pointerInput(count) {
                fun jump(y: Float) {
                    val f = (y / size.height).coerceIn(0f, 1f)
                    scope.launch {
                        state.scrollToItem((f * (count - 1)).toInt().coerceIn(0, count - 1))
                    }
                }
                detectVerticalDragGestures(
                    onDragStart = { jump(it.y) },
                    onVerticalDrag = { ch, _ -> jump(ch.position.y) }
                )
            }
        )
    }
}
