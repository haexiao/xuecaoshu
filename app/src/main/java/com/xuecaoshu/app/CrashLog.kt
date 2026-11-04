package com.xuecaoshu.app

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地崩溃日志：装一个默认的未捕获异常处理器，把堆栈写到 filesDir/crash.log。
 * 不联网、不上报，只在「我的 → 数据」里多出一个「崩溃日志」入口，方便出问题时复制出来。
 */
object CrashLog {

    private const val FILE = "crash.log"
    private const val MAX_CHARS = 20_000

    fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun exists(ctx: Context): Boolean = file(ctx).let { it.exists() && it.length() > 0 }

    fun read(ctx: Context): String = runCatching {
        file(ctx).readText().takeLast(MAX_CHARS)
    }.getOrDefault("（读取失败）")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(app, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(ctx: Context, thread: Thread, t: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val header = buildString {
            append("==== ").append(stamp).append(" ====\n")
            append("设备: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            append("  Android ").append(Build.VERSION.RELEASE)
            append("  versionCode ").append(BuildContext.versionCode).append('\n')
            append("线程: ").append(thread.name).append('\n')
        }
        val body = buildString {
            append(header).append('\n')
            append(t.stackTraceToString())
            append("\n\n")
        }
        val f = file(ctx)
        // 只留最近几段，避免越滚越大
        val old = if (f.exists()) f.readText().takeLast(MAX_CHARS) else ""
        f.writeText((old + body).takeLast(MAX_CHARS))
    }
}

/** 构建版本号，避免 CrashLog 依赖 BuildConfig 的生成顺序。 */
object BuildContext {
    val versionCode: Int get() = runCatching { com.xuecaoshu.app.BuildConfig.VERSION_CODE }
        .getOrDefault(0)
}
