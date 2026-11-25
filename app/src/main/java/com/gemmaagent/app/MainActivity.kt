package com.gemmaagent.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.gemmaagent.app.ui.ChatScreen
import com.gemmaagent.app.ui.MemoryScreen
import com.gemmaagent.app.ui.NotesScreen
import com.gemmaagent.app.ui.OnboardingScreen
import com.gemmaagent.app.ui.SettingsScreen
import com.gemmaagent.app.ui.StatsScreen
import com.gemmaagent.app.ui.theme.GA
import com.gemmaagent.app.ui.theme.GemmaTheme

class MainActivity : FragmentActivity() {

    private var shortcut by mutableStateOf<String?>(null)
    private var unlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        shortcut = intent?.getStringExtra(EXTRA_SHORTCUT)
        val app = GemmaChatApp.instance
        unlocked = savedInstanceState?.getBoolean("unlocked") == true || !app.settings.current.biometricLock

        setContent {
            val s by app.settings.settings.collectAsState()
            GemmaTheme(accent = s.accent, fontScale = s.fontScale.factor) {
                var screen by rememberSaveable { mutableStateOf(if (s.onboardingDone) SCREEN_CHAT else SCREEN_ONBOARDING) }
                Box(Modifier.fillMaxSize().background(GA.Black)) {
                    if (s.biometricLock && !unlocked) {
                        LockScreen { authenticate() }
                    } else {
                        AnimatedContent(
                            targetState = screen,
                            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                            label = "nav",
                        ) { target ->
                            val back = { screen = SCREEN_CHAT }
                            when (target) {
                                SCREEN_ONBOARDING -> OnboardingScreen(
                                    onDone = { app.settings.update { it.copy(onboardingDone = true) }; screen = SCREEN_CHAT },
                                    onOpenSettings = { app.settings.update { it.copy(onboardingDone = true) }; screen = SCREEN_SETTINGS },
                                )
                                SCREEN_SETTINGS -> {
                                    BackHandler { back() }
                                    SettingsScreen(
                                        onBack = back,
                                        onOpenMemory = { screen = SCREEN_MEMORY },
                                        onOpenStats = { screen = SCREEN_STATS },
                                        onReplayOnboarding = { screen = SCREEN_ONBOARDING },
                                    )
                                }
                                SCREEN_MEMORY -> { BackHandler { back() }; MemoryScreen(onBack = back) }
                                SCREEN_NOTES -> {
                                    BackHandler { back() }
                                    NotesScreen(onBack = back, onOpenSession = { id -> app.chat.setSession(id); screen = SCREEN_CHAT })
                                }
                                SCREEN_STATS -> { BackHandler { back() }; StatsScreen(onBack = back) }
                                else -> ChatScreen(
                                    onOpenSettings = { screen = SCREEN_SETTINGS },
                                    onOpenNotes = { screen = SCREEN_NOTES },
                                    onOpenMemory = { screen = SCREEN_MEMORY },
                                    onOpenStats = { screen = SCREEN_STATS },
                                    shortcut = shortcut,
                                    onShortcutConsumed = { shortcut = null },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (!unlocked) authenticate()
    }

    private var backgroundAt = 0L

    override fun onStop() {
        super.onStop()
        backgroundAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // Kunci lagi bila aplikasi ditinggal lebih dari 60 detik.
        if (backgroundAt > 0 && GemmaChatApp.instance.settings.current.biometricLock &&
            System.currentTimeMillis() - backgroundAt > 60_000L
        ) {
            unlocked = false
            authenticate()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_SHORTCUT)?.let { shortcut = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("unlocked", unlocked)
    }

    /** Kunci aplikasi biometrik (fitur 50): sidik jari atau kunci layar bawaan HP. */
    private fun authenticate() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val can = BiometricManager.from(this).canAuthenticate(authenticators)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            // Perangkat tanpa kunci layar: jangan sampai pengguna terkunci di luar aplikasinya sendiri.
            unlocked = true
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                unlocked = true
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Buka GemmaChat Hybrid")
            .setSubtitle("Lindungi riwayat obrolan & memori pribadi")
            .setAllowedAuthenticators(authenticators)
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { unlocked = true }
    }

    companion object {
        const val EXTRA_SHORTCUT = "shortcut"
        private const val SCREEN_ONBOARDING = "onboarding"
        private const val SCREEN_CHAT = "chat"
        private const val SCREEN_SETTINGS = "settings"
        private const val SCREEN_MEMORY = "memory"
        private const val SCREEN_NOTES = "notes"
        private const val SCREEN_STATS = "stats"
    }
}

@androidx.compose.runtime.Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(GA.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Lock, null, tint = GA.TextSecondary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("GemmaChat Hybrid terkunci", color = GA.TextPrimary, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onUnlock) { Text("Buka kunci") }
    }
}
