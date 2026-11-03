package com.xuecaoshu.app

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.SoundEffectConstants

/** 点击音效与触感反馈，全部走系统默认，不引入任何音频资源。 */
object Feedback {
    private var audio: AudioManager? = null
    private var vibrator: Vibrator? = null
    private var soundLevel = 60
    private var vibrateLevel = 50

    fun init(context: Context) {
        val app = context.applicationContext
        audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun configure(sound: Int, vibrate: Int) {
        soundLevel = sound.coerceIn(0, 100)
        vibrateLevel = vibrate.coerceIn(0, 100)
    }

    fun tap() {
        if (soundLevel > 0) {
            val vol = (soundLevel / 100f).coerceIn(0.02f, 1f)
            runCatching { audio?.playSoundEffect(SoundEffectConstants.CLICK, vol) }
        }
        if (vibrateLevel > 0) {
            runCatching {
                val v = vibrator ?: return@runCatching
                if (!v.hasVibrator()) return@runCatching
                val amp = (vibrateLevel / 100f * 255f).toInt().coerceIn(1, 255)
                when {
                    // 高强度用两段脉冲，单次长震在多数机器上感知不出力度
                    vibrateLevel >= 70 -> v.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 32, 22, 38),
                            intArrayOf(0, amp, 0, amp),
                            -1
                        )
                    )
                    vibrateLevel >= 40 -> v.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 26, 16, 26),
                            intArrayOf(0, amp, 0, amp),
                            -1
                        )
                    )
                    else -> v.vibrate(VibrationEffect.createOneShot(22, amp))
                }
            }
        }
    }
}
