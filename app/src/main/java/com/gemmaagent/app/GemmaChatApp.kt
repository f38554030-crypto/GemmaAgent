package com.gemmaagent.app

import android.app.Application
import android.util.Log
import com.gemmaagent.app.chat.ChatController
import com.gemmaagent.app.data.ExportManager
import com.gemmaagent.app.data.ModelRepository
import com.gemmaagent.app.data.SettingsRepository
import com.gemmaagent.app.data.db.AppDatabase
import com.gemmaagent.app.llm.LlmEngine
import com.gemmaagent.app.util.CrashLogger
import com.gemmaagent.app.util.TtsPlayer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Titik awal aplikasi GemmaChat Hybrid: menyimpan objek inti (singleton). */
class GemmaChatApp : Application() {

    /** Error di background tidak boleh membuat aplikasi force close. */
    private val safeHandler = CoroutineExceptionHandler { _, t ->
        Log.e("GemmaChat", "Error di background", t)
        CrashLogger.record(this, "Error di background (aplikasi tidak ditutup)", t)
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + safeHandler)

    lateinit var settings: SettingsRepository
        private set
    lateinit var db: AppDatabase
        private set
    lateinit var llm: LlmEngine
        private set
    lateinit var models: ModelRepository
        private set
    lateinit var chat: ChatController
        private set
    lateinit var export: ExportManager
        private set
    lateinit var tts: TtsPlayer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashLogger.install(this)
        settings = SettingsRepository(this)
        db = AppDatabase.create(this)
        llm = LlmEngine(this)
        models = ModelRepository(this, appScope, settings) { llm.unloadBlocking() }
        chat = ChatController(this, appScope, db, llm, settings, models)
        export = ExportManager(this, db, settings)
        tts = TtsPlayer(this)

        // Muat model otomatis begitu file model terdeteksi / diganti.
        appScope.launch {
            models.active.filterNotNull().distinctUntilChanged().collect { chat.preload() }
        }
        appScope.launch {
            models.scanning.collect { scanning ->
                if (!scanning && models.active.value == null) llm.markNoModel()
            }
        }
    }

    companion object {
        lateinit var instance: GemmaChatApp
            private set
    }
}
