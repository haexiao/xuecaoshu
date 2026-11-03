package com.xuecaoshu.app.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.xuecaoshu.app.AppState
import com.xuecaoshu.app.Feedback
import com.xuecaoshu.app.data.CursiveFace
import com.xuecaoshu.app.data.LearnOrder
import com.xuecaoshu.app.data.RegularFont
import kotlin.math.roundToInt
import java.io.File

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val p = Ink.palette
    BackHandler { onBack() }
    val ctx = LocalContext.current
    val s = AppState.settings
    var showClear by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()?.let { AppState.importArchive(it) }
        }
    }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "‹", color = p.ink2, fontSize = 26.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() }.padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Spacer(Modifier.padding(horizontal = 3.dp))
            Text("我的", color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(2.dp))

            GroupTitle("学习")
            Group {
                DropdownRow(
                    "字库",
                    if (s.level == 1) "GB2312 一级" else "GB2312 二级",
                    listOf("GB2312 一级", "GB2312 二级")
                ) { i -> AppState.updateSettings { it.copy(level = i + 1) } }
                Divider()
                DropdownRow(
                    "学习节奏", "${s.dailyNew} 字 / 天",
                    listOf("5 字 / 天", "10 字 / 天", "15 字 / 天", "20 字 / 天")
                ) { i -> AppState.updateSettings { it.copy(dailyNew = listOf(5, 10, 15, 20)[i]) } }
                Divider()
                DropdownRow(
                    "复习节奏", "${s.dailyReview} 字 / 天",
                    listOf("5 字 / 天", "10 字 / 天", "15 字 / 天", "20 字 / 天")
                ) { i -> AppState.updateSettings { it.copy(dailyReview = listOf(5, 10, 15, 20)[i]) } }
                Divider()
                DropdownRow(
                    "学习顺序",
                    when (s.order) {
                        LearnOrder.FREQ -> "字频顺序"
                        LearnOrder.BOOK -> "字库顺序"
                        else -> "全部乱序"
                    },
                    listOf("字频顺序", "字库顺序", "全部乱序")
                ) { i ->
                    AppState.updateSettings {
                        it.copy(
                            order = when (i) {
                                0 -> LearnOrder.FREQ
                                1 -> LearnOrder.BOOK
                                else -> LearnOrder.RANDOM
                            }
                        )
                    }
                }
            }

            GroupTitle("显示与字体")
            Group {
                SwitchRow("深色模式", s.darkTheme) { v ->
                    AppState.updateSettings { it.copy(darkTheme = v) }
                }
                Divider()
                DropdownRow(
                    "草书字体",
                    when (s.cursiveFace) {
                        CursiveFace.XINGYUN -> "行云草书"
                        CursiveFace.ZHONGQI -> "钟齐流江"
                        else -> "毛泽东字体"
                    },
                    listOf("行云草书", "钟齐流江", "毛泽东字体")
                ) { i ->
                    AppState.updateSettings {
                        it.copy(
                            cursiveFace = when (i) {
                                0 -> CursiveFace.XINGYUN
                                1 -> CursiveFace.ZHONGQI
                                else -> CursiveFace.MAOTI
                            }
                        )
                    }
                }
                Divider()
                DropdownRow(
                    "常规字体",
                    when (s.regularFont) {
                        RegularFont.SYSTEM -> "系统字体"
                        RegularFont.WENKAI -> "霞鹜文楷"
                        else -> "思源黑体"
                    },
                    listOf("系统字体", "霞鹜文楷", "思源黑体")
                ) { i ->
                    AppState.updateSettings {
                        it.copy(
                            regularFont = when (i) {
                                0 -> RegularFont.SYSTEM
                                1 -> RegularFont.WENKAI
                                else -> RegularFont.NOTO
                            }
                        )
                    }
                }

            }

            GroupTitle("声音与触感")
            Group {
                SliderRow("点击音效", s.soundLevel) { v ->
                    AppState.updateSettings { it.copy(soundLevel = v) }
                }
                Divider()
                SliderRow("点击震动", s.vibrateLevel) { v ->
                    AppState.updateSettings { it.copy(vibrateLevel = v) }
                }
            }

            GroupTitle("数据")
            Group {
                ArrowRow("进度导出", "发送存档文件") {
                    runCatching {
                        AppState.persist()
                        val f = File(ctx.filesDir, "progress.json")
                        val uri = FileProvider.getUriForFile(
                            ctx, "${ctx.packageName}.fileprovider", f
                        )
                        val i = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(i, "导出学习进度"))
                    }
                }
                Divider()
                ArrowRow("进度导入", "从存档文件恢复") {
                    importer.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
                Divider()
                ArrowRow("清除学习进度", "清空全部记录") { showClear = true }
            }

            GroupTitle("关于")
            Group {
                ArrowRow("说明", "怎么看怎么用") { showHelp = true }
                Divider()
                ArrowRow("关于本应用", "版本与数据来源") { showAbout = true }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("清除学习进度", color = p.ink) },
            text = {
                Text(
                    "会清空全部已学记录、收藏和统计，设置保留。此操作不可撤销。",
                    color = p.ink2
                )
            },
            confirmButton = {
                Text(
                    "清除",
                    color = p.accent,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable {
                        Feedback.tap()
                        AppState.clearProgress()
                        showClear = false
                    }.padding(8.dp)
                )
            },
            dismissButton = {
                Text(
                    "取消",
                    color = p.muted,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable { showClear = false }.padding(8.dp)
                )
            },
            containerColor = p.card
        )
    }

    if (showHelp) InfoDialog("说明", HELP_TEXT) { showHelp = false }
    if (showAbout) InfoDialog("关于", ABOUT_TEXT) { showAbout = false }
}

private val HELP_TEXT = """
学草书把认字拆成一张一张卡片，和背单词的节奏一样。

· 学习 / 复习
主页两个入口：学习是还没学会的字，复习是到期该巩固的字。一屏一个字，四选一。

· 三个操作
看答案 —— 不选直接看答案，算一次答错。
熟 —— 你已经会了，直接标记学会，明天进复习。
收藏 —— 记不住的字收进来，之后在我的收藏里单独练。

答完自动展开解析：草书、楷书、繁体、繁草一次看全。
点一下答错的那个选项，还能看它自己的写法对照。

· 怎样才算学会
同一个字连对 3 次，顶部三条连击指示全亮就学会。
中途答错一次，连击清零，从头再来。

· 复习间隔
学会后按难度因子 ef 滚动：答对间隔变长，答错回到 1 天。
ef 由字频决定起点（常用字 2.70，罕用字 2.00），答对 +0.10，答错 −0.20。
间隔超过 100 天就毕业，不再自动出现。

· 字库页
可以按 全部 / 未学习 / 复习中 / 复习完成 / 已标熟 筛选，
也能按字频顺序、字库顺序或乱序排序，右侧有快速滚动条。
单击一个字切换草书 / 楷书，长按进入多选，能批量标熟、重学、收藏。

· 学习顺序
字频顺序：先学最常用的字（默认）。
字库顺序：按 GB2312 编码顺序。
全部乱序：随机抽。

· 一言
联网时取自 hitokoto.cn 的诗词与文学句，并缓存上一条。
断网时用缓存或内置的 48 句古诗。点句子切换草书 / 楷书，点「换一句」换一条。
""".trimIndent()

private val ABOUT_TEXT = """
学草书 v1.0　·　单机应用，无账号，无广告。

· 数据来源
字库：GB2312 编码空间（6763 字）。
形近字：Unicode Unihan 的 kRSUnicode（部首）+ kTotalStrokes（笔画）。
繁简对照：Unicode Unihan 的 kTraditionalVariant，共 2329 字。
字频：Jun Da 现代汉语字频表（语料库统计）。
一言：hitokoto.cn。

· 字体
行云草书 —— 随附授权书，见原字体包。
钟齐流江毛笔草体 —— 免费字体。
草檀斋毛泽东字体 —— 见原字体包说明。
霞鹜文楷 —— SIL Open Font License 1.1。
思源黑体（Noto Sans Hans）—— SIL Open Font License 1.1。

· 存档
进度存在应用私有目录的 progress.json，每天自动备份一份 progress.bak.json。
可在「数据」里导出 / 导入。卸载应用会一并删除，换机前记得先导出。
""".trimIndent()

@Composable
private fun GroupTitle(text: String) {
    val p = Ink.palette
    Text(
        text,
        color = p.muted,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    val p = Ink.palette
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card)
    ) { content() }
}

@Composable
private fun Divider() {
    val p = Ink.palette
    Box(
        Modifier.fillMaxWidth().padding(start = 18.dp).height(1.dp)
            .background(p.line.copy(alpha = 0.55f))
    )
}

@Composable
private fun SwitchRow(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val p = Ink.palette
    Row(
        Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = p.ink, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Switch(
            checked = value,
            onCheckedChange = { v ->
                Feedback.tap()
                onChange(v)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.bg,
                checkedTrackColor = p.good,
                uncheckedThumbColor = p.muted,
                uncheckedTrackColor = p.line
            )
        )
    }
}

@Composable
private fun SliderRow(title: String, level: Int, onChange: (Int) -> Unit) {
    val p = Ink.palette
    Row(
        Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = p.ink, fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        LevelSlider(
            level = level,
            onChange = onChange,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (level == 0) "关" else "$level%",
            color = if (level == 0) p.muted else p.ink2,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp)
        )
    }
}

/** 自绘滑条：拖到 0 就是关闭。 */
@Composable
private fun LevelSlider(
    level: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = Ink.palette

    Box(
        modifier
            .height(30.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onChange(((offset.x / size.width) * 100f).roundToInt().coerceIn(0, 100))
                    Feedback.tap()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    onChange(
                        ((change.position.x / size.width) * 100f)
                            .roundToInt().coerceIn(0, 100)
                    )
                }
            }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            val frac = level / 100f

            Box(
                Modifier.align(Alignment.CenterStart)
                    .fillMaxWidth().height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(p.line)
            )
            Box(
                Modifier.align(Alignment.CenterStart)
                    .fillMaxWidth(frac)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (level == 0) p.line else p.accent)
            )
            Box(
                Modifier.align(Alignment.CenterStart)
                    .offset(x = (w * frac - 9.dp).coerceAtLeast((-9).dp))
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (level == 0) p.muted else p.accent)
            )
        }
    }
}

@Composable
private fun DropdownRow(
    title: String,
    value: String,
    options: List<String>,
    onPick: (Int) -> Unit
) {
    val p = Ink.palette
    var expanded by remember { mutableStateOf(false) }
    val menuWidth = 168.dp
    BoxWithConstraints {
        val rowWidth = maxWidth
        Row(
            Modifier.fillMaxWidth().height(58.dp)
                .clickable {
                    Feedback.tap()
                    expanded = true
                }.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = p.ink, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(value, color = p.muted, fontSize = 13.sp)
            Spacer(Modifier.padding(horizontal = 3.dp))
            Text("▾", color = p.muted, fontSize = 10.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(x = (rowWidth - menuWidth).coerceAtLeast(0.dp), y = 0.dp),
            modifier = Modifier.width(menuWidth)
        ) {
            options.forEachIndexed { i, opt ->
                DropdownMenuItem(
                    modifier = Modifier.width(menuWidth),
                    text = {
                        Text(
                            opt,
                            color = if (opt == value) p.accent else p.ink,
                            fontSize = 14.sp,
                            fontWeight = if (opt == value) FontWeight.SemiBold
                            else FontWeight.Normal
                        )
                    },
                    onClick = {
                        Feedback.tap()
                        onPick(i)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ArrowRow(title: String, value: String, onClick: () -> Unit) {
    val p = Ink.palette
    Row(
        Modifier.fillMaxWidth().height(58.dp)
            .clickable {
                Feedback.tap()
                onClick()
            }.padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = p.ink, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = p.muted, fontSize = 13.sp)
        Spacer(Modifier.padding(horizontal = 3.dp))
        Text("›", color = p.muted, fontSize = 15.sp)
    }
}

@Composable
private fun InfoDialog(title: String, body: String, onClose: () -> Unit) {
    val p = Ink.palette
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title, color = p.ink) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(body, color = p.ink2, fontSize = 13.sp, lineHeight = 20.sp)
            }
        },
        confirmButton = {
            Text(
                "知道了",
                color = p.accent,
                fontSize = 15.sp,
                modifier = Modifier.clickable { onClose() }.padding(8.dp)
            )
        },
        containerColor = p.card
    )
}
