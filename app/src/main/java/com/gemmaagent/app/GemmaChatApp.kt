package com.gemmaagent.app

import android.app.Application
import android.util.Log
import com.gemmaagent.app.chat.ChatController
import com.gemmaagent.app.data.ExportManager
import com.gemmaagent.app.data.ModelRepository
import com.gemmaagent.app.data.SettingsRepository
import com.gemmaagent.app.data.db.AppDatabase
import com.gemmaagent.app.data.kb.KnowledgeDb
import com.gemmaagent.app.llm.ModelSettingsStore
import com.gemmaagent.app.llm.UnifiedLlmEngine
import com.gemmaagent.app.util.CrashLogger
import com.gemmaagent.app.util.TtsPlayer
import com.gemmaagent.app.web.WebSearchEngine
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Titik awal GemmaChat Hybrid: objek inti dibuat sekali, fitur berat dimuat saat dibutuhkan. */
class GemmaChatApp : Application() {

    private val safeHandler = CoroutineExceptionHandler { _, t ->
        Log.e("GemmaChat", "Error di background", t)
        CrashLogger.record(this, "Error di background (aplikasi tidak ditutup)", t)
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + safeHandler)

    lateinit var settings: SettingsRepository private set
    lateinit var db: AppDatabase private set
    lateinit var kb: KnowledgeDb private set
    lateinit var llm: UnifiedLlmEngine private set
    lateinit var models: ModelRepository private set
    lateinit var modelSettings: ModelSettingsStore private set
    lateinit var web: WebSearchEngine private set
    lateinit var chat: ChatController private set
    lateinit var export: ExportManager private set
    lateinit var tts: TtsPlayer private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashLogger.install(this)
        settings = SettingsRepository(this)
        db = AppDatabase.create(this)
        kb = KnowledgeDb.create(this)
        llm = UnifiedLlmEngine(this)
        modelSettings = ModelSettingsStore(this)
        models = ModelRepository(this, appScope, settings) { llm.unloadBlocking() }
        web = WebSearchEngine(this, kb)
        chat = ChatController(this, appScope, db, kb, llm, settings, models, modelSettings, web)
        export = ExportManager(this, db, settings)
        tts = TtsPlayer(this)

        // Status awal: tandai "belum ada model" bila pemindaian tidak menemukan file.
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
