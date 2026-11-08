package com.xuecaoshu.app.data

/** One character of a word book. */
data class CharEntry(
    val level: Int,
    val index: Int,
    val char: String
)

/**
 * Learning state of a single character.
 *
 * f1 / f2 are *streaks* (连击) per cursive face: a correct answer adds one,
 * a wrong answer resets that face to zero. Reaching 3 finishes the learning
 * phase. Once learned, ef / interval drive the review schedule.
 */
data class CharProgress(
    val f1: Int = 0,               // 行云 连击
    val f2: Int = 0,               // 钟齐 连击
    val ef: Float = 2.5f,          // 难度因子，1.3 ~ 3.0
    val interval: Int = 0,         // 上次间隔（天）
    val mastered: Boolean = false, // 间隔 > 100 天，退场
    val wrong: Int = 0,
    val starred: Boolean = false,
    val learned: Boolean = false,  // 连击达标，进入复习
    val known: Boolean = false,    // 手动点「熟」标熟的，和正常学会区分开
    val dueAt: Long = 0L,
    val lastSeen: Long = 0L
) {
    // 连击只用 f1；f2 是早期「两种草书都学」留下的字段，保留读兼容不再写入
}

/** Per-day counters used by the charts. */
data class DayStat(
    val learned: Int = 0,
    val reviewed: Int = 0,
    val seconds: Int = 0
)

/** 繁体写法 + 三种草书字体各自有没有这个字形。 */
data class TradVariant(
    val text: String,
    val xy: Boolean,   // 行云
    val zq: Boolean,   // 钟齐
    val mt: Boolean    // 毛体
) {
    fun has(face: Int): Boolean = when (face) {
        1 -> zq
        2 -> mt
        else -> xy
    }
    val any: Boolean get() = xy || zq || mt
}

enum class StudyMode { LEARN, REVIEW, FINAL }

enum class CursiveFace { XINGYUN, ZHONGQI, MAOTI }

enum class RegularFont { SYSTEM, WENKAI, NOTO }

/** 新学的出字顺序。 */
enum class LearnOrder { FREQ, BOOK, RANDOM }

data class Settings(
    val darkTheme: Boolean = false,
    val level: Int = 1,
    val cursiveFace: CursiveFace = CursiveFace.XINGYUN,
    val regularFont: RegularFont = RegularFont.SYSTEM,
    val dailyNew: Int = 15,
    val dailyReview: Int = 15,
    val order: LearnOrder = LearnOrder.FREQ,
    val quoteSize: Int = 48,       // 一言字号 sp，28..80
    val soundLevel: Int = 60,      // 0..100，0 = 关闭
    val vibrateLevel: Int = 50
)
