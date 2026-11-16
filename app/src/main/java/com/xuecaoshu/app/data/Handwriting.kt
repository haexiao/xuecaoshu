package com.xuecaoshu.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.io.File
import java.util.zip.Deflater
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 手写样本的本地存储。
 * 目录：filesDir/handwriting/{img/, index.jsonl}
 * 图片：128×128、8-bit 灰度、白底黑字、无透明通道。
 * index.jsonl 每行 {"file","char","time"}，时间 ISO 8601 本地时间、不带时区偏移。
 */
object HwStore {

    data class Record(val file: String, val char: String, val time: String) {
        val millis: Long
            get() = runCatching {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(time)?.time ?: 0L
            }.getOrDefault(0L)
    }

    fun dir(ctx: Context): File = File(ctx.filesDir, "handwriting").apply { mkdirs() }
    fun imgDir(ctx: Context): File = File(dir(ctx), "img").apply { mkdirs() }
    fun indexFile(ctx: Context): File = File(dir(ctx), "index.jsonl")

    // ------------------------------------------------------------------ 读
    fun all(ctx: Context): List<Record> {
        val f = indexFile(ctx)
        if (!f.exists()) return emptyList()
        return runCatching {
            f.readLines().mapNotNull { line ->
                val s = line.trim()
                if (s.isEmpty()) return@mapNotNull null
                parseLine(s)
            }
        }.getOrDefault(emptyList())
    }

    private fun parseLine(s: String): Record? = runCatching {
        val file = field(s, "file") ?: return null
        val ch = field(s, "char") ?: return null
        val t = field(s, "time") ?: return null
        Record(file, ch, t)
    }.getOrNull()

    /** 极简 JSON 取值：这三个字段都是短字符串，不引第三方解析。 */
    private fun field(line: String, key: String): String? {
        val k = "\"$key\":\""
        val i = line.indexOf(k)
        if (i < 0) return null
        val start = i + k.length
        val end = line.indexOf('"', start)
        if (end < 0) return null
        return line.substring(start, end)
    }

    fun ofChar(ctx: Context, ch: String): List<Record> =
        all(ctx).filter { it.char == ch }.sortedBy { it.millis }

    fun counts(ctx: Context): Map<String, Int> {
        val m = HashMap<String, Int>()
        all(ctx).forEach { m[it.char] = (m[it.char] ?: 0) + 1 }
        return m
    }

    // ------------------------------------------------------------------ 写
    /** 把 128×128 的灰度 PNG 落盘，并在 index.jsonl 追加一行。返回记录。 */
    fun save(ctx: Context, char: String, png: ByteArray): Record {
        val now = Date()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
        var name = "hw_${stamp}_$char.png"
        var f = File(imgDir(ctx), name)
        var n = 2
        // 同一秒重复时加 -2 -3，不覆盖旧文件
        while (f.exists()) {
            name = "hw_${stamp}_${char}-$n.png"
            f = File(imgDir(ctx), name)
            n++
        }
        f.writeBytes(png)

        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(now)
        val rel = "img/$name"
        indexFile(ctx).appendText(
            "{\"file\":\"$rel\",\"char\":\"$char\",\"time\":\"$iso\"}\n"
        )
        return Record(rel, char, iso)
    }

    fun delete(ctx: Context, rec: Record) {
        runCatching { File(dir(ctx), rec.file).delete() }
        val f = indexFile(ctx)
        if (!f.exists()) return
        val kept = f.readLines().filter { !it.contains("\"${rec.file}\"") }
        f.writeText(kept.joinToString("\n").let { if (it.isEmpty()) "" else it + "\n" })
    }

    // ------------------------------------------------------------------ 导出
    /** 打成 zip：manifest.json + index.jsonl + img 目录 */
    fun exportZip(ctx: Context): File {
        val recs = all(ctx)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val out = File(ctx.cacheDir, "handwriting_$stamp.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zos ->
            fun put(name: String, bytes: ByteArray) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
            val exported = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(Date())
            val chars = recs.map { it.char }.toSet().size
            put(
                "manifest.json",
                ("{\"app\":\"xuecaoshu\",\"ver\":\"${com.xuecaoshu.app.BuildConfig.VERSION_NAME}\"," +
                    "\"count\":${recs.size},\"chars\":$chars,\"exported\":\"$exported\"}")
                    .toByteArray(Charsets.UTF_8)
            )
            val idx = indexFile(ctx)
            if (idx.exists()) put("index.jsonl", idx.readBytes())
            recs.forEach { r ->
                val f = File(dir(ctx), r.file)
                if (f.exists()) put(r.file, f.readBytes())
            }
        }
        return out
    }

    // ------------------------------------------------------------------ 落盘渲染
    /**
     * 把笔迹渲染成 128×128 灰度图：
     * 按墨迹外框等比缩放到最大边 126px（98%），笔宽 4.9px，白底黑字。
     */
    fun render(strokes: List<List<androidx.compose.ui.geometry.Offset>>, penAtCanvas: Float): ByteArray? {
        val pts = strokes.flatten()
        if (pts.size < 2) return null

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        pts.forEach {
            if (it.x < minX) minX = it.x
            if (it.x > maxX) maxX = it.x
            if (it.y < minY) minY = it.y
            if (it.y > maxY) maxY = it.y
        }
        val half = penAtCanvas / 2f
        minX -= half; maxX += half; minY -= half; maxY += half
        val w = (maxX - minX).coerceAtLeast(1f)
        val h = (maxY - minY).coerceAtLeast(1f)

        val target = 126f                       // 128 的 98%
        val scale = target / maxOf(w, h)
        // 笔宽随比例缩放，落盘后在 128 下约为 4.9px
        val pen128 = (penAtCanvas * scale).coerceIn(1.5f, 12f)

        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = pen128
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val ox = (128 - w * scale) / 2f
        val oy = (128 - h * scale) / 2f
        strokes.forEach { st ->
            if (st.size < 2) {
                // 单点按一个圆点处理
                if (st.size == 1) {
                    val p = st[0]
                    c.drawCircle(
                        (p.x - minX) * scale + ox, (p.y - minY) * scale + oy,
                        pen128 / 2f, paint.apply { style = Paint.Style.FILL; color = Color.BLACK }
                    )
                    paint.style = Paint.Style.STROKE
                }
                return@forEach
            }
            val path = Path()
            st.forEachIndexed { i, p ->
                val x = (p.x - minX) * scale + ox
                val y = (p.y - minY) * scale + oy
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            c.drawPath(path, paint)
        }

        // 转成 8-bit 灰度并编码（不带透明通道，白底黑字）
        val px = IntArray(128 * 128)
        bmp.getPixels(px, 0, 128, 0, 0, 128, 128)
        val gray = ByteArray(128 * 128)
        for (i in px.indices) {
            val v = px[i]
            val a = (v ushr 24) and 0xFF
            val r = (v ushr 16) and 0xFF
            val g = (v ushr 8) and 0xFF
            val b = v and 0xFF
            // 黑线压在白底上，按 alpha 合成后取亮度
            val lum = (r * 0.299f + g * 0.587f + b * 0.114f)
            val out = lum * (a / 255f) + 255f * (1f - a / 255f)
            gray[i] = out.toInt().coerceIn(0, 255).toByte()
        }
        bmp.recycle()
        return encodeGrayPng(gray, 128, 128)
    }

    /** 最小 PNG 编码器：8-bit 灰度（color type 0），不写透明通道。 */
    private fun encodeGrayPng(pixels: ByteArray, w: Int, h: Int): ByteArray {
        // 每行前面加一个 filter 字节（0 = None）
        val raw = ByteArrayOutputStream(h * (w + 1))
        for (y in 0 until h) {
            raw.write(0)
            raw.write(pixels, y * w, w)
        }
        val deflated = Deflater(9).let { d ->
            d.setInput(raw.toByteArray())
            d.finish()
            val buf = ByteArrayOutputStream()
            val tmp = ByteArray(8192)
            while (!d.finished()) {
                val n = d.deflate(tmp)
                if (n > 0) buf.write(tmp, 0, n)
            }
            d.end()
            buf.toByteArray()
        }

        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        fun be32(v: Int): ByteArray = byteArrayOf(
            (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()
        )
        fun chunk(type: String, data: ByteArray) {
            out.write(be32(data.size))
            val t = type.toByteArray(Charsets.US_ASCII)
            out.write(t)
            out.write(data)
            val crcSrc = t + data
            out.write(be32(crc32(crcSrc)))
        }
        val ihdr = ByteArrayOutputStream().apply {
            write(be32(w)); write(be32(h))
            write(8)        // bit depth
            write(0)        // color type 0 = 灰度
            write(0); write(0); write(0)
        }.toByteArray()
        chunk("IHDR", ihdr)
        chunk("IDAT", deflated)
        chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun crc32(bytes: ByteArray): Int {
        var crc = -1
        for (b in bytes) {
            crc = crc xor (b.toInt() and 0xFF)
            for (k in 0 until 8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0xEDB88320.toInt()
                else crc ushr 1
            }
        }
        return crc.inv()
    }
}
