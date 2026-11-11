package com.xuecaoshu.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.xuecaoshu.app.data.Store
import com.xuecaoshu.app.ui.BookScreen
import com.xuecaoshu.app.ui.HomeScreen
import com.xuecaoshu.app.ui.Ink
import com.xuecaoshu.app.ui.SettingsScreen
import com.xuecaoshu.app.ui.StudyScreen
import com.xuecaoshu.app.ui.XuecaoshuTheme

private enum class Screen { HOME, BOOK, SETTINGS, STUDY }

class MainActivity : ComponentActivity() {
    override fun onPause() {
        super.onPause()
        runCatching { AppState.flush() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Feedback.init(applicationContext)
        CrashLog.install(applicationContext)
        if (!AppState.ready) {
            AppState.init(Store(applicationContext))
        }
        Feedback.configure(AppState.settings.soundLevel, AppState.settings.vibrateLevel)
        setContent {
            val dark = AppState.settings.darkTheme
            val regular = AppState.settings.regularFont
            XuecaoshuTheme(dark = dark, regularFont = regular) {
                val p = Ink.palette
                var screen by remember { mutableStateOf(Screen.HOME) }

                val view = LocalView.current
                SideEffect {
                    val window = (view.context as? Activity)?.window ?: return@SideEffect
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = p.bg) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            // 只用状态栏/导航栏的边距，不含输入法——否则键盘一弹整个界面会被压扁
                            .windowInsetsPadding(WindowInsets.systemBars)
                    ) {
                        AnimatedContent(
                            targetState = screen,
                            transitionSpec = {
                                // 回主页面 = 后退（从右侧滑出），其余 = 前进（从右侧滑入）
                                val back = targetState == Screen.HOME
                                val dir = if (back) -1 else 1
                                (
                                    fadeIn(tween(200)) +
                                        slideInHorizontally(tween(240)) { dir * it / 8 }
                                    ).togetherWith(
                                    fadeOut(tween(160)) +
                                        slideOutHorizontally(tween(240)) { -dir * it / 10 }
                                )
                            },
                            label = "screen"
                        ) { current ->
                        when (current) {
                            Screen.HOME -> HomeScreen(
                                onStartStudy = { mode ->
                                    AppState.startSession(mode)
                                    screen = Screen.STUDY
                                },
                                onOpenBook = { screen = Screen.BOOK },
                                onOpenSettings = { screen = Screen.SETTINGS }
                            )

                            Screen.BOOK -> BookScreen(onBack = { screen = Screen.HOME })
                            Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.HOME })
                            Screen.STUDY -> StudyScreen(onExit = { screen = Screen.HOME })
                        }
                        }
                    }
                }
            }
        }
    }
}
