package com.xuecaoshu.app.data

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 一言：优先取 hitokoto.cn 的诗词/文学句子，失败时回落到内置古诗列表。
 * 全程 4 秒超时，不阻塞主线程。
 */
object QuoteApi {

    private const val ENDPOINT = "https://v1.hitokoto.cn/?c=i&c=d&encode=json"

    /** 兜底用的内置句子。 */
    val FALLBACK = listOf(
        "明月松间照", "清泉石上流", "大漠孤烟直", "长河落日圆",
        "空山新雨后", "天气晚来秋", "山随平野尽", "江入大荒流",
        "海内存知己", "天涯若比邻", "行到水穷处", "坐看云起时",
        "云无心以出岫", "鸟倦飞而知还", "落霞与孤鹜齐飞", "秋水共长天一色",
        "天街小雨润如酥", "草色遥看近却无", "山高月小", "水落石出",
        "野旷天低树", "江清月近人", "月落乌啼霜满天", "江枫渔火对愁眠",
        "孤帆远影碧空尽", "唯见长江天际流", "随风潜入夜", "润物细无声",
        "春潮带雨晚来急", "野渡无人舟自横", "竹外桃花三两枝", "春江水暖鸭先知",
        "不识庐山真面目", "只缘身在此山中", "纸上得来终觉浅", "绝知此事要躬行",
        "问渠那得清如许", "为有源头活水来", "沉舟侧畔千帆过", "病树前头万木春",
        "留连戏蝶时时舞", "自在娇莺恰恰啼", "黑云翻墨未遮山", "白雨跳珠乱入船",
        "莫听穿林打叶声", "何妨吟啸且徐行", "小楼一夜听春雨", "深巷明朝卖杏花"
    )

    data class Quote(val text: String, val from: String, val online: Boolean)

    fun fallback(): Quote =
        Quote(FALLBACK.random(), "", false)

    private const val PREFS = "quote_cache"

    /** 上次成功拿到的那条，冷启动时先用它。 */
    fun loadCache(ctx: Context): Quote? {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val text = sp.getString("text", null) ?: return null
        if (text.isBlank()) return null
        return Quote(text, sp.getString("from", "").orEmpty(), false)
    }

    fun saveCache(ctx: Context, q: Quote) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("text", q.text)
            .putString("from", q.from)
            .putLong("at", System.currentTimeMillis())
            .apply()
    }

    /** 上次成功抓取的时间；0 表示从没成功过。 */
    fun cacheTime(ctx: Context): Long =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("at", 0L)

    /**
     * 同步阻塞调用，请在 IO 线程执行。
     * 返回 null 表示网络不可用或格式不对。
     */
    fun fetchBlocking(): Quote? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "xuecaoshu/1.0")
            }
            if (conn.responseCode !in 200..299) return null
            val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use {
                it.readText()
            }
            val o = JSONObject(body)
            val text = o.optString("hitokoto").replace("\n", "").trim()
            if (text.isEmpty() || text.length > 14) return null   // 竖排放不下长句
            Quote(text, o.optString("from"), true)
        } catch (t: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
