package com.xuecaoshu.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuecaoshu.app.AppState
import com.xuecaoshu.app.Feedback
import com.xuecaoshu.app.data.HwStore
import com.xuecaoshu.app.data.LearnOrder
import java.io.File
import kotlin.math.min

/**
 * 手写练习页。
 * 画布底图 86%、12% 灰；笔宽 3.33% 画布宽（笔宽/字形 = 3.87%），与设计稿一致。
 */
@Composable
fun HandwritingScreen(onExit: () -> Unit) {
    val p = Ink.palette
    val ctx = LocalContext.current

    var baseFace by remember { mutableStateOf(-1) }          // -1 = 常规字体
    var order by remember { mutableStateOf(AppState.settings.order) }
    var faceOpen by remember { mutableStateOf(false) }
    var orderOpen by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }

    val chars = remember(order, AppState.dataVersion) {
        val pool = AppState.charsOfLevel(AppState.settings.level)
        when (order) {
            LearnOrder.FREQ -> pool.sortedBy { AppState.freq[it.char] ?: Int.MAX_VALUE }
            LearnOrder.BOOK -> pool
            else -> pool.shuffled()
        }
    }

    var charIdx by remember { mutableStateOf(0) }
    val cur = chars.getOrNull(charIdx)

    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasPx by remember { mutableStateOf(0f) }

    var records by remember { mutableStateOf(emptyList<HwStore.Record>()) }
    var curRec by remember { mutableStateOf(0) }   // == records.size → 新的一张（草稿）
    var confirmText by remember { mutableStateOf<String?>(null) }
    var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val isDraft = curRec >= records.size
    val dirty = isDraft && (strokes.isNotEmpty() || live.isNotEmpty())

    fun reload(ch: String) {
        records = HwStore.ofChar(ctx, ch)
        curRec = records.size
        strokes.clear()
        live = emptyList()
    }

    LaunchedEffect(cur?.char) { cur?.let { reload(it.char) } }

    fun guard(what: String, block: () -> Unit) {
        if (dirty) {
            confirmText = "$what 前先处理这一张"
            confirmAction = block
        } else block()
    }

    BackHandler { guard("退出") { onExit() } }

    // 已存记录的解码结果（缩略图与预览共用）
    // 样本只取墨线、底色透明：深色白线、浅色黑线，都不出现白底方块
    val decoded = remember(records, AppState.settings.darkTheme) {
        records.map { r ->
            runCatching {
                val bmp = BitmapFactory.decodeFile(File(HwStore.dir(ctx), r.file).path)
                    ?: return@runCatching null
                inkOnly(bmp, AppState.settings.darkTheme).asImageBitmap()
            }.getOrNull()
        }
    }

    Column(Modifier.fillMaxSize().background(p.bg).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))

        // ① 顶栏
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹", color = p.ink2, fontSize = 24.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { Feedback.tap(); guard("退出") { onExit() } }
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(AppState.levelName, color = p.ink2, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            if (cur != null) {
                Text(
                    if (isDraft) "新的一张" else "第 ${curRec + 1} / ${records.size} 张",
                    color = p.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(10.dp))
                Text("${charIdx + 1} / ${chars.size}", color = p.muted, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(6.dp))

        // ② 工具条
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                chip("底图 " + baseName(baseFace) + " ▾") { faceOpen = true }
                DropdownMenu(faceOpen, onDismissRequest = { faceOpen = false }) {
                    listOf(-1 to "常规字体", 0 to "行云草书", 1 to "钟齐流江", 2 to "草檀斋毛泽东")
                        .forEach { (f, name) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        name, fontSize = 13.sp,
                                        color = if (f == baseFace) p.accent else p.ink
                                    )
                                },
                                onClick = { baseFace = f; faceOpen = false }
                            )
                        }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box {
                chip(
                    "顺序 " + when (order) {
                        LearnOrder.FREQ -> "字频"; LearnOrder.BOOK -> "字库"; else -> "乱序"
                    } + " ▾"
                ) { orderOpen = true }
                DropdownMenu(orderOpen, onDismissRequest = { orderOpen = false }) {
                    listOf(
                        LearnOrder.FREQ to "字频顺序",
                        LearnOrder.BOOK to "字库顺序",
                        LearnOrder.RANDOM to "乱序"
                    ).forEach { (o, name) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    name, fontSize = 13.sp,
                                    color = if (o == order) p.accent else p.ink
                                )
                            },
                            onClick = {
                                order = o; orderOpen = false
                                charIdx = 0
                                guard("换顺序") { chars.getOrNull(0)?.let { reload(it.char) } }
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            chip("查看字库") { showLibrary = true }
        }

        Spacer(Modifier.height(10.dp))

        // ③ 画布
        CanvasBox(
            face = baseFace,
            cur = cur?.char,
            strokes = strokes,
            live = live,
            preview = if (!isDraft) decoded.getOrNull(curRec) else null,
            onSize = { canvasPx = it },
            onStart = { off -> if (isDraft) live = listOf(off) },
            onMove = { off -> if (isDraft) live = live + off },
            onEnd = {
                if (isDraft && live.isNotEmpty()) {
                    strokes.add(live)
                    live = emptyList()
                }
            }
        )

        Spacer(Modifier.height(10.dp))

        // ④ 重写 / 撤回 + 三个对照字体（放大）
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(92.dp)) {
                minorBtn("重写", Modifier.fillMaxWidth(), enabled = isDraft) {
                    Feedback.tap(); strokes.clear(); live = emptyList()
                }
                Spacer(Modifier.height(8.dp))
                minorBtn("撤回", Modifier.fillMaxWidth(), enabled = isDraft && strokes.isNotEmpty()) {
                    Feedback.tap()
                    if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1)
                    live = emptyList()
                }
            }
            Spacer(Modifier.width(12.dp))
            val others = listOf(0, 1, 2, -1).filter { it != baseFace }.take(3)
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                others.forEach { f ->
                    Text(
                        cur?.char ?: "",
                        color = p.ink2,
                        fontSize = 54.sp,
                        fontFamily = if (f < 0) FontFamily.Default else cursiveFamily(f)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ⑤ 新建 / 保存 / 删除
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            mainBtn(
                "新建", Modifier.weight(1f),
                bg = p.card, fg = p.ink2,
                enabled = isDraft.not() || strokes.isNotEmpty() || live.isNotEmpty()
            ) {
                Feedback.tap()
                guard("新建") {
                    curRec = records.size
                    strokes.clear(); live = emptyList()
                }
            }
            mainBtn(
                "保存", Modifier.weight(1f),
                bg = p.ink, fg = onColor(p.ink),
                enabled = isDraft && (strokes.isNotEmpty() || live.isNotEmpty())
            ) {
                Feedback.tap()
                val ch = cur?.char ?: return@mainBtn
                val png = HwStore.render(
                    strokes.toList(),
                    penAtCanvas = (if (canvasPx > 0f) canvasPx else 360f) * 0.0333f
                )
                if (png != null) {
                    HwStore.save(ctx, ch, png)
                    records = HwStore.ofChar(ctx, ch)
                    curRec = records.size - 1          // 跳到刚存的那张
                    strokes.clear(); live = emptyList()
                }
            }
            mainBtn(
                "删除", Modifier.weight(1f),
                bg = p.accent, fg = onColor(p.accent),
                enabled = !isDraft
            ) {
                Feedback.tap()
                if (!isDraft) {
                    HwStore.delete(ctx, records[curRec])
                    val ch = cur?.char ?: return@mainBtn
                    records = HwStore.ofChar(ctx, ch)
                    curRec = records.size
                    strokes.clear(); live = emptyList()
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ⑥ 已写的图：缩略图条（新的一张在最右）
        ThumbStrip(
            count = records.size,
            current = curRec,
            thumbs = decoded,
            onPick = { i ->
                Feedback.tap()
                guard("切换") {
                    curRec = i
                    strokes.clear(); live = emptyList()
                }
            }
        )

        Spacer(Modifier.weight(1f))

        // ⑦ 大按钮：上一张/下一张 + 上一字/下一字
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            bigBtn("‹ 上一张", Modifier.weight(1f), enabled = curRec > 0) {
                Feedback.tap()
                guard("切换") { curRec--; strokes.clear(); live = emptyList() }
            }
            bigBtn("下一张 ›", Modifier.weight(1f), enabled = curRec < records.size) {
                Feedback.tap()
                guard("切换") { curRec++; strokes.clear(); live = emptyList() }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            bigBtn("‹ 上一字", Modifier.weight(1f), primary = true, enabled = charIdx > 0) {
                Feedback.tap()
                guard("换字") {
                    charIdx--
                    chars.getOrNull(charIdx)?.let { reload(it.char) }
                }
            }
            bigBtn("下一字 ›", Modifier.weight(1f), primary = true, enabled = charIdx < chars.size - 1) {
                Feedback.tap()
                guard("换字") {
                    charIdx++
                    chars.getOrNull(charIdx)?.let { reload(it.char) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    // 未保存提醒
    confirmText?.let { msg ->
        AlertDialog(
            onDismissRequest = { confirmText = null; confirmAction = null },
            containerColor = p.card,
            title = { Text("这一张还没保存", color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text("$msg，会丢掉现在写的笔迹。", color = p.ink2, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    val act = confirmAction
                    confirmText = null; confirmAction = null
                    strokes.clear(); live = emptyList()
                    act?.invoke()
                }) {
                    Text("不保存", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmText = null; confirmAction = null }) {
                    Text("继续写", color = p.ink2, fontSize = 14.sp)
                }
            }
        )
    }

    // 查看字库：直接复用「全部字库」那一屏（选择模式 + 张数角标）
    if (showLibrary) {
        BackHandler { showLibrary = false }
        CharListScreen(
            onlyStarred = false,
            pickMode = true,
            badges = HwStore.counts(ctx),
            onPick = { ch ->
                showLibrary = false
                val i = chars.indexOfFirst { it.char == ch }
                if (i >= 0) {
                    guard("换字") {
                        charIdx = i
                        reload(ch)
                    }
                }
            },
            onBack = { showLibrary = false }
        )
    }
}

private fun baseName(f: Int): String = when (f) {
    0 -> "行云草书"
    1 -> "钟齐流江"
    2 -> "草檀斋毛泽东"
    else -> "常规字体"
}

@Composable
private fun chip(text: String, onClick: () -> Unit) {
    val p = Ink.palette
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(p.card)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = p.ink2, fontSize = 12.sp)
    }
}

@Composable
private fun minorBtn(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val p = Ink.palette
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
            .background(p.card)
            .border(1.dp, p.line, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) p.ink2 else p.muted.copy(alpha = 0.4f),
            fontSize = 13.sp
        )
    }
}

@Composable
private fun mainBtn(
    text: String,
    modifier: Modifier = Modifier,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val p = Ink.palette
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (enabled) bg else p.card)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) fg else p.muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 底部大按钮，三档配色：
 * 张维度 = 卡片底 + 墨色字；字维度 = 砖红实心；其余 = 卡片底 + 灰字。
 */
@Composable
private fun bigBtn(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val p = Ink.palette
    val bg = when {
        !enabled -> p.card
        primary -> p.accent
        else -> p.card
    }
    Box(
        modifier.height(50.dp).clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = when {
                !enabled -> p.muted.copy(alpha = 0.45f)
                primary -> onColor(p.accent)
                else -> p.ink
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 已写图片的缩略图条：点一张就切过去，最后一格是「新的一张」。 */
@Composable
private fun ThumbStrip(
    count: Int,
    current: Int,
    thumbs: List<ImageBitmap?>,
    onPick: (Int) -> Unit
) {
    val p = Ink.palette
    val scroll = rememberScrollState()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val on = i == current
            Box(
                Modifier.size(60.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (AppState.settings.darkTheme) p.card else WHITE_BG)
                    .border(if (on) 2.dp else 1.dp, if (on) p.accent else p.line,
                        RoundedCornerShape(8.dp))
                    .clickable { onPick(i) },
                contentAlignment = Alignment.Center
            ) {
                thumbs.getOrNull(i)?.let { bmp ->
                    androidx.compose.foundation.Image(
                        bitmap = bmp,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(3.dp)
                    )
                }
            }
        }
        // 新的一张
        val onNew = current >= count
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(9.dp))
                .background(p.card)
                .border(
                    if (onNew) 2.dp else 1.dp,
                    if (onNew) p.accent else p.line,
                    RoundedCornerShape(8.dp)
                )
                .clickable { onPick(count) },
            contentAlignment = Alignment.Center
        ) {
            Text("＋", color = if (onNew) p.accent else p.muted, fontSize = 20.sp)
        }
    }
}

/** 画布：草稿可写；已存记录只显示那张图，小尺寸居中。 */
@Composable
private fun CanvasBox(
    face: Int,
    cur: String?,
    strokes: List<List<Offset>>,
    live: List<Offset>,
    preview: ImageBitmap?,
    onSize: (Float) -> Unit,
    onStart: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onEnd: () -> Unit
) {
    val p = Ink.palette
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (AppState.settings.darkTheme) p.card else WHITE_BG)
            .border(1.dp, p.line, RoundedCornerShape(16.dp))
            .onSizeChanged { onSize(it.width.toFloat()) }
            .then(
                if (preview == null) Modifier.pointerInput(cur, preview) {
                    detectDragGestures(
                        onDragStart = { onStart(it) },
                        onDrag = { change, _ -> change.consume(); onMove(change.position) },
                        onDragEnd = { onEnd() },
                        onDragCancel = { onEnd() }
                    )
                } else Modifier
            )
    ) {
        val side = min(maxWidth.value, maxHeight.value)
        val baseStyle = TextStyle(
            fontSize = (side * 0.86f).sp,
            fontFamily = if (face < 0) FontFamily.Default else cursiveFamily(face),
            color = p.ink.copy(alpha = 0.12f)
        )
        val baseLayout = if (cur != null && preview == null) {
            runCatching { measurer.measure(cur, baseStyle) }.getOrNull()
        } else null

        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension
            if (preview != null) {
                // 已存的一张：按「略大于原始像素」显示，不撑满画布
                val w = s * 0.56f
                val left = (size.width - w) / 2f
                val top = (size.height - w) / 2f
                drawImage(
                    preview,
                    dstOffset = IntOffset(left.toInt(), top.toInt()),
                    dstSize = IntSize(w.toInt(), w.toInt())
                )
            } else if (baseLayout != null) {
                drawText(
                    baseLayout,
                    topLeft = Offset(
                        (size.width - baseLayout.size.width) / 2f,
                        (size.height - baseLayout.size.height) / 2f
                    )
                )
            }
            val pen = s * 0.0333f
            val ink = if (AppState.settings.darkTheme) p.good else p.accent
            (strokes + listOf(live)).forEach { st ->
                if (st.size < 2) return@forEach
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(st[0].x, st[0].y)
                    for (i in 1 until st.size) lineTo(st[i].x, st[i].y)
                }
                drawPath(
                    path, ink,
                    style = Stroke(
                        width = pen,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }
        }

        if (preview != null) {
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp)).background(p.card)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text("已保存的一张", color = p.muted, fontSize = 9.sp)
            }
        }
    }
}

private val WHITE_BG = androidx.compose.ui.graphics.Color(0xFF, 0xFF, 0xFF)

/**
 * 只取墨迹：白底变透明。
 * 深色模式下线条画成浅色、浅色模式下画成深色，两种情况都不出现白底方块。
 */
private fun inkOnly(src: android.graphics.Bitmap, dark: Boolean): android.graphics.Bitmap {
    val w = src.width
    val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    val rgb = if (dark) 0x00F2EFE8 else 0x001E1B18
    val out = IntArray(w * h)
    for (i in px.indices) {
        val v = px[i]
        val r = (v ushr 16) and 0xFF
        val g = (v ushr 8) and 0xFF
        val b = v and 0xFF
        val gray = (r * 299 + g * 587 + b * 114) / 1000
        out[i] = ((255 - gray) shl 24) or rgb
    }
    return android.graphics.Bitmap.createBitmap(out, w, h, android.graphics.Bitmap.Config.ARGB_8888)
}
