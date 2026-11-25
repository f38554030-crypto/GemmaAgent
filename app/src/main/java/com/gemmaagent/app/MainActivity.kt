package com.gemmaagent.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gemmaagent.app.service.AgentForegroundService
import com.gemmaagent.app.ui.ChatScreen
import com.gemmaagent.app.ui.DashboardScreen
import com.gemmaagent.app.ui.theme.GA
import com.gemmaagent.app.ui.theme.GemmaAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        setContent {
            GemmaAgentTheme {
                var screen by rememberSaveable { mutableStateOf(SCREEN_DASHBOARD) }
                Box(Modifier.fillMaxSize().background(GA.Black)) {
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            val forward = targetState == SCREEN_CHAT
                            (slideInHorizontally(tween(320)) { w -> if (forward) w / 3 else -w / 3 } + fadeIn(tween(320)))
                                .togetherWith(slideOutHorizontally(tween(320)) { w -> if (forward) -w / 3 else w / 3 } + fadeOut(tween(220)))
                        },
                        label = "screen",
                    ) { target ->
                        when (target) {
                            SCREEN_CHAT -> {
                                BackHandler { screen = SCREEN_DASHBOARD }
                                ChatScreen(onBack = { screen = SCREEN_DASHBOARD })
                            }
                            else -> DashboardScreen(onStartChat = {
                                AgentForegroundService.start(this@MainActivity)
                                screen = SCREEN_CHAT
                            })
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val SCREEN_DASHBOARD = "dashboard"
        private const val SCREEN_CHAT = "chat"
    }
}
