package com.gemmaagent.app.llm

/** Deklarasi fungsi native llama.cpp (lihat app/src/main/cpp/llama_jni.cpp). */
object LlamaNative {
    fun interface LoadProgress { fun onProgress(progress: Float): Boolean }
    fun interface TokenCallback { fun onToken(piece: ByteArray): Boolean }

    @Volatile
    private var loaded = false

    @Synchronized
    fun ensureLibrary(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("gemmachat_llama")
            nativeInit()
            loaded = true
            true
        } catch (t: Throwable) {
            false
        }
    }

    external fun nativeInit()
    external fun nativeLoad(path: String, nCtx: Int, nThreads: Int, progress: LoadProgress?): Int
    external fun nativeUnload()
    external fun nativeIsLoaded(): Boolean
    external fun nativeInfo(): ByteArray?
    external fun nativeFormatChat(roles: Array<ByteArray>, contents: Array<ByteArray>, thinking: Boolean): ByteArray?
    external fun nativeStop()
    external fun nativeClearCache()
    external fun nativeGenerate(
        prompt: ByteArray, temp: Float, topP: Float, topK: Int, repeatPenalty: Float, maxTokens: Int, callback: TokenCallback,
    ): LongArray
    external fun nativeBench(pp: Int, tg: Int): DoubleArray
}
