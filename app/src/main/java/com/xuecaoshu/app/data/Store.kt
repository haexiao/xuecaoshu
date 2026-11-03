package com.xuecaoshu.app.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Disk side of the app: character books, similarity/variant tables,
 * progress archive, settings. Everything is plain JSON/CSV so a progress
 * export is just a copy of one file.
 */
class Store(private val ctx: Context) {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun today(): String = dateFmt.format(Date())

    // ---------------------------------------------------------------- books
    fun loadBooks(): List<CharEntry> {
        val out = ArrayList<CharEntry>(7000)
        ctx.assets.open("characters.csv").bufferedReader().useLines { lines ->
            lines.forEachIndexed { i, line ->
                if (i == 0 || line.isBlank()) return@forEachIndexed
                val parts = line.split(',')
                if (parts.size >= 3) {
                    val lv = parts[0].trim().toIntOrNull() ?: return@forEachIndexed
                    val idx = parts[1].trim().toIntOrNull() ?: return@forEachIndexed
                    out.add(CharEntry(lv, idx, parts[2].trim()))
                }
            }
        }
        return out
    }

    /** char -> list of look-alike candidates (same radical + close strokes). */
    fun loadSimilar(): Map<String, List<String>> {
        val map = HashMap<String, List<String>>(8000)
        ctx.assets.open("similar.csv").bufferedReader().useLines { lines ->
            lines.forEachIndexed { i, line ->
                if (i == 0 || line.isBlank()) return@forEachIndexed
                val parts = line.split(',')
                if (parts.size >= 2) {
                    map[parts[0].trim()] = parts[1].split('|').filter { it.isNotBlank() }
                }
            }
        }
        return map
    }

    /** simplified -> traditional, only for characters whose form actually differs. */
    fun loadVariants(): Map<String, TradVariant> {
        val map = HashMap<String, TradVariant>(3000)
        ctx.assets.open("variants.csv").bufferedReader().useLines { lines ->
            lines.forEachIndexed { i, line ->
                if (i == 0 || line.isBlank()) return@forEachIndexed
                val parts = line.split(',')
                if (parts.size >= 2) {
                    fun flag(i: Int) = parts.size > i && parts[i].trim() == "1"
                    map[parts[0].trim()] = TradVariant(
                        text = parts[1].trim(),
                        xy = if (parts.size <= 2) true else flag(2),
                        zq = flag(3),
                        mt = flag(4)
                    )
                }
            }
        }
        return map
    }

    /** char -> 字频排名（1 = 最常用）；0 表示词表里没有它。 */
    fun loadFreq(): Map<String, Int> {
        val map = HashMap<String, Int>(8000)
        runCatching {
            ctx.assets.open("freq.csv").bufferedReader().useLines { lines ->
                lines.forEachIndexed { i, line ->
                    if (i == 0 || line.isBlank()) return@forEachIndexed
                    val parts = line.split(',')
                    if (parts.size >= 2) {
                        val rank = parts[1].trim().toIntOrNull() ?: 0
                        if (rank > 0) map[parts[0].trim()] = rank
                    }
                }
            }
        }
        return map
    }

    // ------------------------------------------------------------- progress
    private fun progressFile() = File(ctx.filesDir, "progress.json")
    private fun backupFile() = File(ctx.filesDir, "progress.bak.json")

    /** 每天留一份备份，主文件被清空或写坏时还能捞回来。 */
    fun backupIfStale() {
        runCatching {
            val f = progressFile()
            if (!f.exists() || f.length() < 20) return
            val bak = backupFile()
            val stale = !bak.exists() ||
                System.currentTimeMillis() - bak.lastModified() > 24L * 3600_000L
            if (stale) f.copyTo(bak, overwrite = true)
        }
    }

    /** 主文件不见了但备份还在，就用备份顶上。 */
    fun restoreFromBackupIfMissing(): Boolean = runCatching {
        val f = progressFile()
        val bak = backupFile()
        if (f.exists() && f.length() >= 20) return false
        if (!bak.exists() || bak.length() < 20) return false
        bak.copyTo(f, overwrite = true)
        true
    }.getOrDefault(false)

    fun loadProgress(): MutableMap<String, CharProgress> {
        val map = HashMap<String, CharProgress>()
        restoreFromBackupIfMissing()
        val f = progressFile()
        if (!f.exists()) return map
        runCatching {
            val root = JSONObject(f.readText())
            val chars = root.optJSONObject("chars") ?: JSONObject()
            chars.keys().forEach { ch ->
                val o = chars.getJSONObject(ch)
                // schema 2: f1/f2 是「累计答对次数」，l 表示已学会
                // schema 3: f1/f2 变成「连击」，多出 ef / interval / mastered
                val learned = o.optBoolean("l", false)
                val f1 = o.optInt("f1", o.optInt("c", 0))
                val f2 = o.optInt("f2", 0)
                val ef = o.optDouble("ef", 2.5).toFloat()
                val interval = o.optInt("iv", 0)
                // schema 2 里 f1/f2 是「累计答对次数」，当连击用会虚高，封顶到 3；
                // schema 3 起它们就是连击，复习答对还能继续加，不能再压。
                val legacy = root.optInt("schema", 2) < 3
                map[ch] = CharProgress(
                    f1 = if (legacy) f1.coerceIn(0, 3) else f1,
                    f2 = if (legacy) f2.coerceIn(0, 3) else f2,
                    ef = ef.coerceIn(1.3f, 3.0f),
                    interval = interval,
                    mastered = o.optBoolean("m", false),
                    wrong = o.optInt("w", 0),
                    starred = o.optBoolean("s", false),
                    learned = learned,
                    dueAt = o.optLong("d", 0L),
                    lastSeen = o.optLong("t", 0L)
                )
            }
        }
        return map
    }

    fun saveProgress(progress: Map<String, CharProgress>, stats: Map<String, DayStat>) {
        runCatching {
            val root = JSONObject()
            val chars = JSONObject()
            progress.forEach { (ch, p) ->
                if (p == CharProgress()) return@forEach
                chars.put(ch, JSONObject().apply {
                    put("f1", p.f1)
                    put("f2", p.f2)
                    put("ef", p.ef.toDouble())
                    put("iv", p.interval)
                    if (p.mastered) put("m", true)
                    put("w", p.wrong)
                    put("s", p.starred)
                    put("l", p.learned)
                    put("d", p.dueAt)
                    put("t", p.lastSeen)
                })
            }
            root.put("chars", chars)
            val st = JSONObject()
            stats.forEach { (day, s) ->
                st.put(day, JSONObject().apply {
                    put("l", s.learned)
                    put("r", s.reviewed)
                    put("s", s.seconds)
                })
            }
            root.put("stats", st)
            root.put("exportedAt", System.currentTimeMillis())
            root.put("schema", 3)
            progressFile().writeText(root.toString())
        }
    }

    fun loadStats(): MutableMap<String, DayStat> {
        val map = HashMap<String, DayStat>()
        val f = progressFile()
        if (!f.exists()) return map
        runCatching {
            val st = JSONObject(f.readText()).optJSONObject("stats") ?: return map
            st.keys().forEach { day ->
                val o = st.getJSONObject(day)
                map[day] = DayStat(o.optInt("l", 0), o.optInt("r", 0), o.optInt("s", 0))
            }
        }
        return map
    }

    fun importFromText(text: String): Boolean = runCatching {
        JSONObject(text)
        progressFile().writeText(text)
        true
    }.getOrDefault(false)

    // ------------------------------------------------------------- settings
    private val prefs by lazy { ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    fun loadSettings(): Settings = Settings(
        darkTheme = prefs.getBoolean("dark", false),
        level = prefs.getInt("level", 1),
        cursiveFace = when (prefs.getInt("face", 0)) {
            1 -> CursiveFace.ZHONGQI
            2 -> CursiveFace.MAOTI
            else -> CursiveFace.XINGYUN
        },
        regularFont = when (prefs.getInt("rfont", 0)) {
            0 -> RegularFont.SYSTEM
            1 -> RegularFont.WENKAI
            else -> RegularFont.NOTO
        },
        dailyNew = prefs.getInt("dailyNew", 15),
        dailyReview = prefs.getInt("dailyReview", 15),
        order = when (prefs.getInt("order", 0)) {
            1 -> LearnOrder.BOOK
            2 -> LearnOrder.RANDOM
            else -> LearnOrder.FREQ
        },
        soundLevel = prefs.getInt(
            "soundLevel",
            if (prefs.getBoolean("sound", true)) 60 else 0
        ),
        vibrateLevel = prefs.getInt(
            "vibrateLevel",
            if (prefs.getBoolean("vibrate", true)) 50 else 0
        )
    )

    fun saveSettings(s: Settings) {
        prefs.edit()
            .putBoolean("dark", s.darkTheme)
            .putInt("level", s.level)
            .putInt("face", when (s.cursiveFace) {
                CursiveFace.XINGYUN -> 0
                CursiveFace.ZHONGQI -> 1
                CursiveFace.MAOTI -> 2
            })
            .putInt("rfont", when (s.regularFont) {
                RegularFont.SYSTEM -> 0
                RegularFont.WENKAI -> 1
                else -> 2
            })
            .putInt("dailyNew", s.dailyNew)
            .putInt("dailyReview", s.dailyReview)
            .putInt("order", when (s.order) {
                LearnOrder.FREQ -> 0
                LearnOrder.BOOK -> 1
                LearnOrder.RANDOM -> 2
            })
            .putInt("soundLevel", s.soundLevel)
            .putInt("vibrateLevel", s.vibrateLevel)
            .apply()
    }
}
