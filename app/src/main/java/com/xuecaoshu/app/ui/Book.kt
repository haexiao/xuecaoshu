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
enum class Period { WEEK, MONTH, YEAR }

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
    var period by remember { mutableStateOf(Period.WEEK) }
    var selected by remember { mutableStateOf(-1) }

    val total = AppState.charsOfLevel(AppState.settings.level).size
    val learned = AppState.learnedCount()
    val ratio = if (total == 0) 0f else learned.toFloat() / total
    val starred = AppState.starredChars().size
    val today = AppState.todayStat()
    val all = AppState.totalStat()

    val series = when (period) {
        Period.WEEK -> AppState.weekSeries()
        Period.MONTH -> AppState.monthSeries()
        Period.YEAR -> AppState.yearSeries()
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

        Spacer(Modifier.height(2.dp))
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
            Chip("周", period == Period.WEEK) { period = Period.WEEK; selected = -1 }
            Spacer(Modifier.size(6.dp))
            Chip("月", period == Period.MONTH) { period = Period.MONTH; selected = -1 }
            Spacer(Modifier.size(6.dp))
            Chip("年", period == Period.YEAR) { period = Period.YEAR; selected = -1 }
        }
        Spacer(Modifier.height(10.dp))
        TrendChart(
            series, metric, period, selected,
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
    period: Period,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    val p = Ink.palette

    val labels = remember(series, period) {
        series.mapIndexed { i, (key, _) -> axisLabel(key, period, i, series.size) }
    }

    val learnedVals = series.map { it.second.learned }
    val reviewVals = series.map { it.second.reviewed }
    val timeVals = series.map { it.second.seconds }

    val maxTotal = if (metric == Metric.COUNT) {
        series.maxOfOrNull { it.second.learned + it.second.reviewed } ?: 0
    } else {
        timeVals.maxOrNull() ?: 0
    }.coerceAtLeast(1)

    Column(modifier.fillMaxWidth()) {
        // 柱子区域占满剩下的高度，一直画到屏幕底
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f)
        ) {
            val areaH = maxHeight
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                series.forEachIndexed { i, _ ->
                    val on = i == selected
                    val a = learnedVals[i]
                    val b = reviewVals[i]
                    val total = if (metric == Metric.COUNT) a + b else timeVals[i]

                    Box(
                        Modifier.weight(1f).fillMaxHeight().pointerInput(i) {
                            detectTapGestures { onSelect(if (selected == i) -1 else i) }
                        },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (total == 0) {
                            Box(
                                Modifier.fillMaxWidth().height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(p.line)
                            )
                        } else {
                            val barW = if (on) 1f else 0.82f
                            Column(
                                Modifier.fillMaxWidth(barW),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                if (metric == Metric.COUNT) {
                                    if (b > 0) {
                                        Box(
                                            Modifier.fillMaxWidth()
                                                .height(areaH * (b.toFloat() / maxTotal))
                                                .clip(
                                                    RoundedCornerShape(
                                                        topStart = 4.dp, topEnd = 4.dp
                                                    )
                                                )
                                                .background(
                                                    if (on) p.good
                                                    else p.good.copy(alpha = 0.65f)
                                                )
                                        )
                                    }
                                    if (a > 0) {
                                        Box(
                                            Modifier.fillMaxWidth()
                                                .height(areaH * (a.toFloat() / maxTotal))
                                                .clip(
                                                    if (b > 0) RoundedCornerShape(0.dp)
                                                    else RoundedCornerShape(
                                                        topStart = 4.dp, topEnd = 4.dp
                                                    )
                                                )
                                                .background(
                                                    if (on) p.accent
                                                    else p.accent.copy(alpha = 0.6f)
                                                )
                                        )
                                    }
                                } else {
                                    Box(
                                        Modifier.fillMaxWidth()
                                            .height(
                                                (areaH * (total.toFloat() / maxTotal))
                                                    .coerceAtLeast(3.dp)
                                            )
                                            .clip(
                                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                            )
                                            .background(
                                                if (on) p.ink else p.ink.copy(alpha = 0.45f)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { i, label ->
                Text(
                    label,
                    color = if (i == selected) p.accent else p.muted,
                    fontSize = 8.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (metric == Metric.COUNT) {
                Legend(p.accent, "新学")
                Spacer(Modifier.width(14.dp))
                Legend(p.good, "复习")
            } else {
                Legend(p.ink.copy(alpha = 0.45f), "学习时长")
            }
        }

        if (selected in series.indices) {
            Spacer(Modifier.height(8.dp))
            val st = series[selected].second
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(p.card).padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(series[selected].first, color = p.muted, fontSize = 11.sp)
                    Spacer(Modifier.size(12.dp))
                    if (metric == Metric.COUNT) {
                        Text("新学 ${st.learned} 字", color = p.accent, fontSize = 12.sp)
                        Spacer(Modifier.size(12.dp))
                        Text("复习 ${st.reviewed} 字", color = p.good, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Text("共 ${st.learned + st.reviewed} 字", color = p.ink2, fontSize = 12.sp)
                    } else {
                        Text(
                            "共 ${ceil(st.seconds / 60.0).toInt()} 分钟",
                            color = p.ink2, fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 图例小方块。 */
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
private fun axisLabel(key: String, period: Period, index: Int, size: Int): String {
    return when (period) {
        Period.WEEK -> {
            val cal = Calendar.getInstance()
            cal.time = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .parse(key) ?: return ""
            val names = arrayOf("日", "一", "二", "三", "四", "五", "六")
            names[cal.get(Calendar.DAY_OF_WEEK) - 1]
        }
        Period.MONTH -> {
            if (index % 5 == 0 || index == size - 1) key.takeLast(2) else ""
        }
        Period.YEAR -> key.takeLast(2)
    }
}

// ------------------------------------------------------------------ 字表
/** 列表里每一格需要的东西，全部提前算好，滚动时不再查 map。 */
private data class CharCell(
    val char: String,
    val index: Int,
    val learned: Boolean,
    val mastered: Boolean,
    val starred: Boolean,
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
                4 -> g.starred
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
