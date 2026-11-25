package com.gemmaagent.app

import android.app.Application
import com.gemmaagent.app.agent.AgentController
import com.gemmaagent.app.data.ChatRepository
import com.gemmaagent.app.data.ModelRepository
import com.gemmaagent.app.llm.LlmEngine
import com.gemmaagent.app.util.CrashLogger
import com.gemmaagent.app.util.FeedbackManager
import com.gemmaagent.app.util.Notifications
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Titik awal aplikasi. Menyimpan objek-objek inti (singleton) yang dipakai
 * bersama oleh layar, Foreground Service, dan Accessibility Service.
 */
class GemmaAgentApp : Application() {

    /** Penangkap error terakhir: error di background tidak boleh membuat aplikasi force close. */
    val safeHandler = CoroutineExceptionHandler { _, t ->
        Log.e("GemmaAgent", "Error di background", t)
        CrashLogger.record(this, "Error di background (aplikasi tidak ditutup)", t)
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + safeHandler)

    lateinit var modelRepository: ModelRepository
        private set
    lateinit var llm: LlmEngine
        private set
    lateinit var chat: ChatRepository
        private set
    lateinit var feedback: FeedbackManager
        private set
    lateinit var agent: AgentController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashLogger.install(this)
        Notifications.createChannels(this)
        llm = LlmEngine(this)
        modelRepository = ModelRepository(this, appScope) { llm.unloadBlocking() }
        chat = ChatRepository(this, appScope)
        feedback = FeedbackManager(this)
        agent = AgentController(this, appScope, llm, chat, feedback)
    }

    companion object {
        lateinit var instance: GemmaAgentApp
            private set
    }
}
