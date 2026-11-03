package com.xuecaoshu.app

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xuecaoshu.app.data.CharEntry
import com.xuecaoshu.app.data.CharProgress
import com.xuecaoshu.app.data.DayStat
import com.xuecaoshu.app.data.LearnOrder
import com.xuecaoshu.app.data.Settings
import com.xuecaoshu.app.data.TradVariant
import com.xuecaoshu.app.data.Store
import com.xuecaoshu.app.data.StudyMode
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

private const val DAY_MS = 24L * 60L * 60L * 1000L

/** 连击达标线：连对 3 次。 */
const val STREAK_GOAL = 3

/** 间隔超过这个天数就算毕业。 */
private const val MASTER_DAYS = 100

/** A single quiz card. */
data class Card(
    val id: Long = 0L,             // 每张卡唯一，用来驱动换卡动画
    val entry: CharEntry,
    val promptIsCursive: Boolean,
    val face: Int,                 // 0 = 行云, 1 = 钟齐
    val options: List<String>,
    val correctIndex: Int
)

/** Running state of one study session. */
class StudySession(val mode: StudyMode, queue: List<CharEntry>) {
    /** 本轮还没毕业的字（学习模式用来算进度）。 */
    val pool = ArrayList(queue)
    /** 出题队列：字答完后按「间隔」重新插回这里，实现穿插。 */
    val upcoming = ArrayDeque<String>().apply { queue.forEach { add(it.char) } }
    /** 最近出过的字，用来保证同一个字至少隔两张卡。 */
    val recent = ArrayDeque<String>()
    var total by mutableStateOf(queue.size)
    var done by mutableStateOf(0)
    var current by mutableStateOf<Card?>(null)
    var chosenIndex by mutableStateOf(-1)
    var revealed by mutableStateOf(false)
    /** option currently being inspected after answering */
    var inspectIndex by mutableStateOf(-1)
    var firstTryCorrect by mutableStateOf(0)

    val answered: Boolean get() = chosenIndex != -1
    val correct: Boolean get() = chosenIndex >= 0 && chosenIndex == current?.correctIndex
}

object AppState {
    private lateinit var store: Store
    var ready by mutableStateOf(false)
        private set

    private val byLevel = HashMap<Int, List<CharEntry>>(2)
    private val EMPTY = CharProgress()
    var book by mutableStateOf<List<CharEntry>>(emptyList())
        private set
    var similar by mutableStateOf<Map<String, List<String>>>(emptyMap())
        private set
    var variants by mutableStateOf<Map<String, TradVariant>>(emptyMap())
        private set
    var freq by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    val progress = mutableStateMapOf<String, CharProgress>()
    val stats = mutableStateMapOf<String, DayStat>()
    var settings by mutableStateOf(Settings())
        private set

    var session by mutableStateOf<StudySession?>(null)

    /** 进度每次落盘都 +1，页面靠它判断要不要重算列表。 */
    var dataVersion by mutableStateOf(0)
        private set

    private var cardSeq = 0L
    private var tickAt = 0L

    // ------------------------------------------------------------------ init
    fun init(s: Store) {
        store = s
        book = s.loadBooks()
        byLevel.clear()
        book.groupBy { it.level }.forEach { (k, v) -> byLevel[k] = v }
        similar = s.loadSimilar()
        variants = s.loadVariants()
        freq = s.loadFreq()
        s.restoreFromBackupIfMissing()
        progress.putAll(s.loadProgress())
        stats.putAll(s.loadStats())
        s.backupIfStale()
        settings = s.loadSettings()
        Feedback.configure(settings.soundLevel, settings.vibrateLevel)
        ready = true
    }

    // --------------------------------------------------------------- queries
    /** level: 1 = 一级，2 = 二级，0 = 一级 + 二级。 */
    fun charsOfLevel(level: Int): List<CharEntry> = when (level) {
        0 -> (byLevel[1] ?: emptyList()) + (byLevel[2] ?: emptyList())
        else -> byLevel[level] ?: emptyList()
    }

    val levelName: String
        get() = when (settings.level) {
            0 -> "GB2312 一级 + 二级"
            2 -> "GB2312 二级"
            else -> "GB2312 一级"
        }

    fun progressOf(ch: String): CharProgress = progress[ch] ?: EMPTY

    /** 没记录时按字频给一个 ef 初值。 */
    private fun baseProgress(ch: String): CharProgress =
        progress[ch] ?: CharProgress(ef = efPrior(ch))

    fun freqRank(ch: String): Int = freq[ch] ?: Int.MAX_VALUE

    /** 字频 → ef 先验：越罕用起点越低。 */
    fun efPrior(ch: String): Float {
        val r = freq[ch] ?: Int.MAX_VALUE
        return when {
            r <= 500 -> 2.70f
            r <= 1500 -> 2.55f
            r <= 3000 -> 2.40f
            r <= 5000 -> 2.20f
            else -> 2.00f
        }
    }

    /** 某个字已经连对几次。 */
    fun streakOf(ch: String): Int = progress[ch]?.f1 ?: 0

    private fun reachedGoal(p: CharProgress): Boolean = p.f1 >= STREAK_GOAL

    /** Traditional form, or null when it is identical. */
    fun traditionalOf(ch: String): String? = variants[ch]?.text?.takeIf { it != ch }

    /**
     * 繁体草书用哪个字体：优先当前选中的草书字体，没有字形就退回行云；
     * 三个字体都没有返回 -1（那就只显示楷体繁体）。
     */
    fun tradFaceOf(ch: String): Int {
        val v = variants[ch] ?: return -1
        val cur = settings.cursiveFace.ordinal
        if (v.has(cur)) return cur
        if (v.xy) return 0
        return -1
    }

    fun unlearnedCount(): Int =
        charsOfLevel(settings.level).count { !progressOf(it.char).learned }

    fun learnedCount(): Int =
        charsOfLevel(settings.level).count {
            val p = progressOf(it.char); p.learned && !p.mastered
        }

    fun masteredCount(): Int =
        charsOfLevel(settings.level).count { progressOf(it.char).mastered }

    fun dueCount(): Int {
        val now = System.currentTimeMillis()
        return charsOfLevel(settings.level).count {
            val p = progressOf(it.char)
            p.learned && !p.mastered && p.dueAt <= now
        }
    }

    fun starredChars(): List<CharEntry> = book.filter { progressOf(it.char).starred }

    fun todayStat(): DayStat = stats[store.today()] ?: DayStat()

    fun totalStat(): DayStat = stats.values.fold(DayStat()) { a, b ->
        DayStat(a.learned + b.learned, a.reviewed + b.reviewed, a.seconds + b.seconds)
    }

    private val dayFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)

    private fun dayKey(offset: Int): String {
        val c = java.util.Calendar.getInstance()
        c.add(java.util.Calendar.DAY_OF_YEAR, -offset)
        return dayFmt.format(c.time)
    }

    /** 7 days, oldest first. */
    fun weekSeries(): List<Pair<String, DayStat>> =
        (6 downTo 0).map { dayKey(it) to (stats[dayKey(it)] ?: DayStat()) }

    /** 30 days, oldest first. */
    fun monthSeries(): List<Pair<String, DayStat>> =
        (29 downTo 0).map { dayKey(it) to (stats[dayKey(it)] ?: DayStat()) }

    /** 12 months of the current year, oldest first. */
    fun yearSeries(): List<Pair<String, DayStat>> {
        val cal = java.util.Calendar.getInstance()
        val year = cal.get(java.util.Calendar.YEAR)
        val mFmt = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
        val out = ArrayList<Pair<String, DayStat>>(12)
        for (m in 0 until 12) {
            val c = java.util.Calendar.getInstance()
            c.set(year, m, 1, 0, 0, 0)
            c.set(java.util.Calendar.MILLISECOND, 0)
            val key = mFmt.format(c.time)
            val sum = stats.entries.filter { it.key.startsWith(key) }
                .fold(DayStat()) { a, b ->
                    DayStat(a.learned + b.value.learned, a.reviewed + b.value.reviewed,
                        a.seconds + b.value.seconds)
                }
            out.add(key to sum)
        }
        return out
    }

    // -------------------------------------------------------------- settings
    fun updateSettings(block: (Settings) -> Settings) {
        settings = block(settings)
        store.saveSettings(settings)
        Feedback.configure(settings.soundLevel, settings.vibrateLevel)
    }

    // --------------------------------------------------------------- session
    fun buildQueue(mode: StudyMode): List<CharEntry> {
        val pool = charsOfLevel(settings.level)
        val now = System.currentTimeMillis()
        return when (mode) {
            StudyMode.LEARN -> {
                val fresh = pool.filter { !progressOf(it.char).learned }
                val ordered = when (settings.order) {
                    LearnOrder.FREQ -> fresh.sortedBy { freqRank(it.char) }
                    LearnOrder.BOOK -> fresh
                    LearnOrder.RANDOM -> fresh.shuffled(Random(System.nanoTime()))
                }
                ordered.take(settings.dailyNew)
            }

            StudyMode.REVIEW -> pool.filter {
                val p = progressOf(it.char)
                p.learned && !p.mastered && p.dueAt <= now
            }.sortedBy { progressOf(it.char).dueAt }.take(settings.dailyReview)

            StudyMode.FINAL -> pool.filter {
                val p = progressOf(it.char); p.learned
            }.shuffled(Random(System.nanoTime())).take(settings.dailyReview)
        }
    }

    fun startSession(mode: StudyMode) {
        tickAt = SystemClock.elapsedRealtime()
        // 学习模式：今天没学完的那一轮接着学
        if (mode == StudyMode.LEARN) {
            val restored = restoreSession()
            if (restored != null) {
                session = restored
                nextCard()
                return
            }
        }
        session = StudySession(mode, buildQueue(mode))
        nextCard()
        saveSessionState()
    }

    fun endSession() {
        saveSessionState()
        session = null
    }

    // ------------------------------------------------------------ 会话存档
    private fun entryOf(ch: String): CharEntry? =
        charsOfLevel(settings.level).firstOrNull { it.char == ch }

    /** 只存「今天这一轮学习还没学完的部分」。 */
    private fun saveSessionState() {
        val s = session
        if (s == null || s.mode != StudyMode.LEARN) {
            store.clearSession()
            return
        }
        val alive = s.pool.filter { !progressOf(it.char).learned }
        if (alive.isEmpty()) {
            store.clearSession()
            return
        }
        val o = JSONObject()
        o.put("day", store.today())
        o.put("total", s.total)
        o.put("done", s.done)
        val arr = JSONArray()
        alive.forEach { arr.put(it.char) }
        o.put("pool", arr)
        store.saveSession(o.toString())
    }

    private fun restoreSession(): StudySession? {
        val raw = store.loadSession() ?: return null
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (o.optString("day") != store.today()) {
            store.clearSession()
            return null
        }
        val arr = o.optJSONArray("pool") ?: return null
        val ents = ArrayList<CharEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val ch = arr.optString(i)
            if (ch.isEmpty()) continue
            entryOf(ch)?.let { ents.add(it) }
        }
        if (ents.isEmpty()) {
            store.clearSession()
            return null
        }
        val s = StudySession(StudyMode.LEARN, ents)
        s.upcoming.clear()
        ents.shuffled().forEach { s.upcoming.add(it.char) }
        s.total = o.optInt("total", ents.size).coerceAtLeast(ents.size)
        s.done = o.optInt("done", 0).coerceIn(0, s.total)
        return s
    }

    fun nextCard() {
        val s = session ?: return
        s.chosenIndex = -1
        s.revealed = false
        s.inspectIndex = -1
        if (s.mode == StudyMode.LEARN) {
            // 连击达标的字移出本轮
            s.pool.removeAll { progressOf(it.char).learned }
            s.upcoming.removeAll { progressOf(it).learned }
            s.done = s.total - s.pool.size
            if (s.pool.isEmpty()) {
                // 这一轮学完了，清掉存档
                store.clearSession()
                s.current = null
                return
            }
            if (s.upcoming.isEmpty()) {
                // 兜底：队列空了就把剩下没毕业的重新打乱排一遍
                s.upcoming.addAll(s.pool.map { it.char }.shuffled())
            }
            val ch = s.upcoming.removeFirst()
            s.current = makeCard(entryOf(ch) ?: s.pool.first())
        } else {
            if (s.pool.isEmpty()) {
                s.current = null
                return
            }
            s.done = s.total - s.pool.size
            s.current = makeCard(s.pool.removeAt(0))
        }
    }

    /**
     * 出题间隔：答错很快回来，连对越多隔得越远。
     * 20 个字的池子里，这样会形成 A B C D … A B C D 的穿插，而不是
     * 先把几个字刷毕业再换下一批。
     */
    private fun reschedule(s: StudySession, ch: String, wrong: Boolean) {
        if (s.mode != StudyMode.LEARN) return
        val p = progressOf(ch)
        if (p.learned) return                       // 已经毕业，不再排
        val (lo, hi) = when {
            wrong -> 1 to 3
            p.f1 <= 1 -> 3 to 6
            else -> 6 to 10
        }
        val gap = Random.nextInt(lo, hi + 1)
        val pos = gap.coerceAtMost(s.upcoming.size)
        s.upcoming.add(pos, ch)
    }

    private fun makeCard(entry: CharEntry): Card {
        val pool = charsOfLevel(settings.level)
        val correct = entry.char


        // 干扰项：优先取形近字
        val picked = LinkedHashSet<String>()
        similar[correct]?.forEach { c ->
            if (picked.size < 3 && c != correct && c.length == 1) picked.add(c)
        }
        var guard = 0
        while (picked.size < 3 && guard < 400) {
            guard++
            val c = pool[Random.nextInt(pool.size)].char
            if (c != correct) picked.add(c)
        }
        val opts = (picked + correct).shuffled(Random(System.nanoTime()))
        return Card(
            id = cardSeq++,
            entry = entry,
            promptIsCursive = Random.nextBoolean(),
            // 0 = 行云，1 = 钟齐，2 = 毛体，跟着「草书字体」设置走
            face = settings.cursiveFace.ordinal,
            options = opts,
            correctIndex = opts.indexOf(correct)
        )
    }

    // ---------------------------------------------------------------- answer
    fun answer(index: Int) {
        val s = session ?: return
        if (s.answered) return
        val card = s.current ?: return
        s.chosenIndex = index
        s.inspectIndex = index
        val isRight = index == card.correctIndex
        if (isRight) s.firstTryCorrect++

        val now = System.currentTimeMillis()
        val old = baseProgress(card.entry.char)
        val tomorrow = now + DAY_MS

        val np = if (isRight) {
            val nv = old.face(card.face) + 1
            var p = old.withFace(card.face, nv)
            when {
                !p.learned && reachedGoal(p) ->
                    // 刚连对 3 次：进复习队列，明天第一次复习
                    p.copy(learned = true, interval = 1, dueAt = tomorrow, lastSeen = now)

                p.learned -> {
                    val ni = (p.interval.coerceAtLeast(1) * p.ef).toInt().coerceIn(1, 400)
                    val nef = (p.ef + 0.1f).coerceAtMost(3.0f)
                    if (ni > MASTER_DAYS) {
                        p.copy(
                            interval = ni, ef = nef, mastered = true,
                            dueAt = Long.MAX_VALUE, lastSeen = now
                        )
                    } else {
                        p.copy(interval = ni, ef = nef, dueAt = now + ni * DAY_MS, lastSeen = now)
                    }
                }

                else -> p.copy(lastSeen = now)
            }
        } else {
            var p = old.withFace(card.face, 0)
                .copy(wrong = old.wrong + 1, lastSeen = now)
            p = if (old.learned) {
                p.copy(
                    interval = 1,
                    ef = (old.ef - 0.2f).coerceAtLeast(1.3f),
                    dueAt = tomorrow
                )
            } else {
                p.copy(dueAt = now)
            }
            p
        }

        progress[card.entry.char] = np
        reschedule(s, card.entry.char, wrong = !isRight)
        bumpStats(if (s.mode == StudyMode.LEARN) "l" else "r")
        persist()
        saveSessionState()
    }

    /** 答题后点其它选项：只切换正在查看的对照，不改判分 */
    fun inspect(index: Int) {
        val s = session ?: return
        if (!s.answered) return
        s.inspectIndex = index
    }

    fun reveal() {
        val s = session ?: return
        if (s.answered) return
        val card = s.current ?: return
        s.revealed = true
        s.chosenIndex = -2
        val now = System.currentTimeMillis()
        val old = baseProgress(card.entry.char)
        var np = old.withFace(card.face, 0)
            .copy(wrong = old.wrong + 1, lastSeen = now)
        np = if (old.learned) {
            np.copy(interval = 1, ef = (old.ef - 0.2f).coerceAtLeast(1.3f), dueAt = now + DAY_MS)
        } else {
            np.copy(dueAt = now)
        }
        progress[card.entry.char] = np
        reschedule(s, card.entry.char, wrong = true)
        bumpStats(if (s.mode == StudyMode.LEARN) "l" else "r")
        persist()
    }

    fun markKnown() {
        val s = session ?: return
        val card = s.current ?: return
        markKnownBatch(listOf(card.entry.char))
        advance()
    }

    // ------------------------------------------------------------ 批量编辑
    /** 批量标熟：直接算学会，明天进复习。 */
    fun markKnownBatch(chars: Collection<String>) {
        val now = System.currentTimeMillis()
        chars.forEach { ch ->
            val old = baseProgress(ch)
            progress[ch] = old.copy(
                f1 = STREAK_GOAL, f2 = STREAK_GOAL,
                learned = true, mastered = false, interval = 1,
                dueAt = now + DAY_MS, lastSeen = now
            )
        }
        persist()
    }

    /** 批量重学：连击清零、退回复习队列之外，收藏与错次保留。 */
    fun resetBatch(chars: Collection<String>) {
        chars.forEach { ch ->
            val old = progress[ch] ?: return@forEach
            progress[ch] = CharProgress(
                ef = efPrior(ch),
                wrong = old.wrong,
                starred = old.starred
            )
        }
        persist()
    }

    fun starBatch(chars: Collection<String>, on: Boolean) {
        chars.forEach { ch ->
            val old = progressOf(ch)
            progress[ch] = old.copy(starred = on)
        }
        persist()
    }

    fun toggleStar() {
        val s = session ?: return
        val card = s.current ?: return
        val old = progressOf(card.entry.char)
        progress[card.entry.char] = old.copy(starred = !old.starred)
        persist()
    }

    fun advance() {
        val s = session ?: return
        s.done += 1
        nextCard()
    }

    // ----------------------------------------------------------------- stats
    private fun bumpStats(kind: String) {
        val key = store.today()
        val cur = stats[key] ?: DayStat()
        val now = SystemClock.elapsedRealtime()
        val secs = ((now - tickAt) / 1000L).toInt().coerceIn(0, 1800)
        tickAt = now
        stats[key] = when (kind) {
            "l" -> cur.copy(learned = cur.learned + 1, seconds = cur.seconds + secs)
            else -> cur.copy(reviewed = cur.reviewed + 1, seconds = cur.seconds + secs)
        }
    }

    /** 写盘放到单线程队列，答题时不再阻塞主线程；按顺序写，不会互相覆盖。 */
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "progress-io").apply { isDaemon = true }
    }

    fun persist() {
        val chars = progress.toMap()
        val days = stats.toMap()
        dataVersion++
        io.execute { runCatching { store.saveProgress(chars, days) } }
    }

    /** 退出前把队列里的写盘都做完。 */
    fun flush() {
        runCatching {
            val f = java.util.concurrent.FutureTask<Unit> { }
            io.execute(f)
            f.get(2, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    // -------------------------------------------------------------- archive
    /** 清空全部学习进度与统计（设置保留）。 */
    fun clearProgress() {
        progress.clear()
        stats.clear()
        persist()
    }

    fun importArchive(bytes: ByteArray): Boolean = runCatching {
        val ok = store.importFromText(String(bytes, Charsets.UTF_8))
        if (ok) {
            progress.clear()
            progress.putAll(store.loadProgress())
            stats.clear()
            stats.putAll(store.loadStats())
            dataVersion++
        }
        ok
    }.getOrDefault(false)
}
