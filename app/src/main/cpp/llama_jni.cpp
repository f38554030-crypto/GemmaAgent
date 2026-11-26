// Jembatan JNI antara Kotlin (LlamaCppEngine) dan llama.cpp untuk model .gguf.
// - Model dimuat dengan mmap (dibaca langsung dari penyimpanan, tidak disalin ke RAM sekaligus).
// - Prefix KV-cache dipakai ulang antar giliran chat agar prompt tidak diproses ulang dari awal.
// - Semua teks dilewatkan sebagai byte UTF-8 (aman untuk emoji).

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <cmath>
#include <cstring>
#include <string>
#include <vector>

#include "llama.h"
#include "common.h"
#include "chat.h"
#include "sampling.h"

#define TAG "GemmaChatLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model              *g_model   = nullptr;
static llama_context            *g_ctx     = nullptr;
static common_chat_templates_ptr g_tmpls;
static std::vector<llama_token>  g_cached;          // token yang sudah ada di KV cache (seq 0)
static std::atomic<bool>         g_abort{false};
static int                       g_threads = 4;
static constexpr int             BATCH = 256;

static void log_cb(ggml_log_level level, const char *text, void *) {
    if (level >= GGML_LOG_LEVEL_WARN) __android_log_print(ANDROID_LOG_WARN, TAG, "%s", text);
}

static bool abort_cb(void *) { return g_abort.load(); }

static std::string bytes_to_string(JNIEnv *env, jbyteArray arr) {
    if (!arr) return {};
    const jsize n = env->GetArrayLength(arr);
    std::string s(static_cast<size_t>(n), '\0');
    if (n > 0) env->GetByteArrayRegion(arr, 0, n, reinterpret_cast<jbyte *>(&s[0]));
    return s;
}

static jbyteArray string_to_bytes(JNIEnv *env, const std::string &s) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(s.size()));
    if (!s.empty()) env->SetByteArrayRegion(arr, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    return arr;
}

// Panjang awalan UTF-8 yang lengkap (sisa byte yang belum lengkap ditahan untuk token berikutnya).
static size_t valid_utf8_prefix(const std::string &s) {
    size_t i = 0, last_ok = 0;
    while (i < s.size()) {
        const unsigned char c = static_cast<unsigned char>(s[i]);
        size_t len = (c < 0x80) ? 1 : ((c >> 5) == 0x6) ? 2 : ((c >> 4) == 0xE) ? 3 : ((c >> 3) == 0x1E) ? 4 : 1;
        if (i + len > s.size()) break;
        i += len;
        last_ok = i;
    }
    return last_ok;
}

static void free_context() {
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    g_cached.clear();
}

static bool create_context(int n_ctx) {
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = static_cast<uint32_t>(n_ctx);
    cp.n_batch = BATCH;
    cp.n_ubatch = BATCH;
    cp.n_threads = g_threads;
    cp.n_threads_batch = g_threads;
    cp.abort_callback = abort_cb;
    cp.abort_callback_data = nullptr;
    g_ctx = llama_init_from_model(g_model, cp);
    return g_ctx != nullptr;
}

struct ProgressCtx {
    JNIEnv *env;
    jobject cb;
    jmethodID mid;
};

static bool progress_cb(float p, void *ud) {
    auto *pc = static_cast<ProgressCtx *>(ud);
    if (!pc || !pc->cb) return true;
    const jboolean keep = pc->env->CallBooleanMethod(pc->cb, pc->mid, static_cast<jfloat>(p));
    if (pc->env->ExceptionCheck()) { pc->env->ExceptionClear(); return true; }
    return keep == JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_gemmaagent_app_llm_LlamaNative_nativeInit(JNIEnv *, jobject) {
    llama_log_set(log_cb, nullptr);
    llama_backend_init();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gemmaagent_app_llm_LlamaNative_nativeLoad(JNIEnv *env, jobject, jstring jpath, jint n_ctx, jint n_threads, jobject progress) {
    // Strict unload-before-load: lepas model lama sepenuhnya dulu.
    free_context();
    g_tmpls.reset();
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_abort = false;
    g_threads = n_threads > 0 ? n_threads : 4;

    const char *cpath = env->GetStringUTFChars(jpath, nullptr);
    std::string path(cpath);
    env->ReleaseStringUTFChars(jpath, cpath);

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    mp.load_mode = LLAMA_LOAD_MODE_MMAP;   // use_mmap = true

    ProgressCtx pc{env, progress, nullptr};
    if (progress) {
        jclass cls = env->GetObjectClass(progress);
        pc.mid = env->GetMethodID(cls, "onProgress", "(F)Z");
        if (pc.mid) {
            mp.progress_callback = progress_cb;
            mp.progress_callback_user_data = &pc;
        }
    }

    g_model = llama_model_load_from_file(path.c_str(), mp);
    if (!g_model) { LOGE("gagal memuat model: %s", path.c_str()); return 1; }

    if (!create_context(n_ctx)) {
        llama_model_free(g_model);
        g_model = nullptr;
        return 2;
    }
    try {
        g_tmpls = common_chat_templates_init(g_model, "");
    } catch (const std::exception &e) {
        LOGE("template chat tidak bisa dibaca: %s", e.what());
        g_tmpls.reset();
    }
    LOGI("model dimuat: %s (ctx=%d, threads=%d)", path.c_str(), n_ctx, g_threads);
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_gemmaagent_app_llm_LlamaNative_nativeUnload(JNIEnv *, jobject) {
    free_context();
    g_tmpls.reset();
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_gemmaagent_app_llm_LlamaNative_nativeIsLoaded(JNIEnv *, jobject) {
    return (g_model && g_ctx) ? JNI_TRUE : JNI_FALSE;
}

// Info model untuk Inspektor Metadata: desc \n ukuran \n parameter \n ctx_train \n sumber template
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_gemmaagent_app_llm_LlamaNative_nativeInfo(JNIEnv *env, jobject) {
    if (!g_model) return nullptr;
    char desc[256];
    llama_model_desc(g_model, desc, sizeof(desc));
    const char *tmpl = llama_model_chat_template(g_model, nullptr);
    std::string out = std::string(desc) + "\n" +
                      std::to_string(llama_model_size(g_model)) + "\n" +
                      std::to_string(llama_model_n_params(g_model)) + "\n" +
                      std::to_string(llama_model_n_ctx_train(g_model)) + "\n" +
                      (tmpl ? tmpl : "");
    return string_to_bytes(env, out);
}

// Format percakapan dengan chat template bawaan model (Jinja). null jika gagal.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_gemmaagent_app_llm_LlamaNative_nativeFormatChat(JNIEnv *env, jobject, jobjectArray roles, jobjectArray contents, jboolean thinking) {
    if (!g_tmpls) return nullptr;
    try {
        common_chat_templates_inputs in;
        const jsize n = env->GetArrayLength(roles);
        for (jsize i = 0; i < n; i++) {
            auto jr = static_cast<jbyteArray>(env->GetObjectArrayElement(roles, i));
            auto jc = static_cast<jbyteArray>(env->GetObjectArrayElement(contents, i));
            common_chat_msg m;
            m.role = bytes_to_string(env, jr);
            m.content = bytes_to_string(env, jc);
            env->DeleteLocalRef(jr);
            env->DeleteLocalRef(jc);
            in.messages.push_back(m);
        }
        in.add_generation_prompt = true;
        in.use_jinja = true;
        in.enable_thinking = thinking == JNI_TRUE;
        auto params = common_chat_templates_apply(g_tmpls.get(), in);
        return string_to_bytes(env, params.prompt.str());
    } catch (const std::exception &e) {
        LOGE("format chat gagal: %s", e.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gemmaagent_app_llm_LlamaNative_nativeStop(JNIEnv *, jobject) {
    g_abort = true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_gemmaagent_app_llm_LlamaNative_nativeClearCache(JNIEnv *, jobject) {
    if (g_ctx) llama_memory_clear(llama_get_memory(g_ctx), true);
    g_cached.clear();
}

static int decode_range(common_batch &batch, const std::vector<llama_token> &toks, size_t from, bool last_logit) {
    for (size_t i = from; i < toks.size(); i += BATCH) {
        if (g_abort) return -100;
        const size_t end = std::min(toks.size(), i + BATCH);
        batch.clear();
        for (size_t j = i; j < end; j++) {
            batch.add(toks[j], static_cast<llama_pos>(j), 0, last_logit && j == toks.size() - 1);
        }
        const int r = llama_process(g_ctx, LLAMA_PROCESS_TYPE_DECODE, batch.get());
        if (r != 0) return r;
    }
    return 0;
}

// Hasil: [token_prompt, token_dipakai_ulang, token_dihasilkan, us_prompt, us_generate, status]
// status: 0 = selesai, 1 = dihentikan, 2 = batas panjang, <0 = error
static jlongArray generate_impl(JNIEnv *env, jbyteArray jprompt, jfloat temp, jfloat top_p, jint top_k,
                                jfloat repeat_penalty, jint max_tokens, jobject callback) {
    jlong res[6] = {0, 0, 0, 0, 0, 0};
    auto finish = [&](jlong status) {
        res[5] = status;
        jlongArray arr = env->NewLongArray(6);
        env->SetLongArrayRegion(arr, 0, 6, res);
        return arr;
    };
    if (!g_model || !g_ctx) return finish(-1);
    g_abort = false;

    jclass cls = env->GetObjectClass(callback);
    jmethodID on_token = env->GetMethodID(cls, "onToken", "([B)Z");
    if (!on_token) return finish(-2);

    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    std::string prompt = bytes_to_string(env, jprompt);

    // Hindari BOS ganda bila template sudah menuliskannya.
    bool add_special = true;
    const llama_token bos = llama_vocab_bos(vocab);
    if (bos != LLAMA_TOKEN_NULL) {
        const std::string bos_text = common_token_to_piece(vocab, bos, true);
        if (!bos_text.empty() && prompt.rfind(bos_text, 0) == 0) add_special = false;
    }
    std::vector<llama_token> tokens = common_tokenize(vocab, prompt, add_special, true);

    const int n_ctx = static_cast<int>(llama_n_ctx(g_ctx));
    const int reserve = std::max(16, std::min(static_cast<int>(max_tokens), n_ctx / 2));
    if (static_cast<int>(tokens.size()) > n_ctx - reserve) {
        // Terlalu panjang: buang bagian tengah lama, simpan token pertama (BOS) + bagian akhir.
        const int keep_tail = n_ctx - reserve - 1;
        std::vector<llama_token> t;
        t.push_back(tokens.front());
        t.insert(t.end(), tokens.end() - keep_tail, tokens.end());
        tokens.swap(t);
    }
    res[0] = static_cast<jlong>(tokens.size());

    // ---- Pakai ulang prefix KV cache ----
    size_t n_keep = 0;
    while (n_keep < g_cached.size() && n_keep < tokens.size() && g_cached[n_keep] == tokens[n_keep]) n_keep++;
    if (n_keep == tokens.size() && n_keep > 0) n_keep--;   // minimal 1 token harus diproses untuk logits
    llama_memory_t mem = llama_get_memory(g_ctx);
    if (n_keep < g_cached.size()) {
        if (!llama_memory_seq_rm(mem, 0, static_cast<llama_pos>(n_keep), -1)) {
            llama_memory_clear(mem, true);
            n_keep = 0;
        }
    }
    g_cached.assign(tokens.begin(), tokens.begin() + static_cast<long>(n_keep));
    res[1] = static_cast<jlong>(n_keep);

    common_batch batch(g_ctx);
    const int64_t t0 = ggml_time_us();
    const int r = decode_range(batch, tokens, n_keep, true);
    const int64_t t1 = ggml_time_us();
    res[3] = t1 - t0;
    if (r != 0) {
        llama_memory_clear(mem, true);
        g_cached.clear();
        return finish(r == -100 || g_abort ? 1 : -3);
    }
    g_cached = tokens;

    // ---- Sampler ----
    common_params_sampling sp;
    sp.temp = temp;
    sp.top_p = top_p;
    sp.top_k = top_k;
    sp.min_p = 0.05f;
    sp.penalty_repeat = repeat_penalty;
    sp.penalty_last_n = 64;
    common_sampler *smpl = common_sampler_init(g_model, sp);
    if (!smpl) return finish(-4);

    std::string pending;
    jlong status = 0;
    int generated = 0;
    llama_pos pos = static_cast<llama_pos>(tokens.size());
    const int64_t t2 = ggml_time_us();
    while (true) {
        if (g_abort) { status = 1; break; }
        if (generated >= max_tokens || pos >= n_ctx - 1) { status = 2; break; }

        const llama_token id = common_sampler_sample(smpl, g_ctx, -1);
        common_sampler_accept(smpl, id, true);
        if (llama_vocab_is_eog(vocab, id)) break;

        pending += common_token_to_piece(vocab, id, false);
        const size_t ok = valid_utf8_prefix(pending);
        if (ok > 0) {
            jbyteArray piece = string_to_bytes(env, pending.substr(0, ok));
            const jboolean go = env->CallBooleanMethod(callback, on_token, piece);
            env->DeleteLocalRef(piece);
            pending.erase(0, ok);
            if (env->ExceptionCheck()) { env->ExceptionClear(); status = 1; break; }
            if (go != JNI_TRUE) { status = 1; generated++; break; }
        }

        batch.clear();
        batch.add(id, pos, 0, true);
        if (llama_process(g_ctx, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) {
            status = g_abort ? 1 : -5;
            break;
        }
        g_cached.push_back(id);
        pos++;
        generated++;
    }
    res[4] = ggml_time_us() - t2;
    res[2] = generated;
    common_sampler_free(smpl);
    if (status < 0) {
        llama_memory_clear(mem, true);
        g_cached.clear();
    }
    return finish(status);
}

// Benchmark mini: [pp tok/s, tg tok/s]
static jdoubleArray bench_impl(JNIEnv *env, jint pp, jint tg) {
    jdouble out[2] = {0, 0};
    jdoubleArray arr = env->NewDoubleArray(2);
    if (!g_model || !g_ctx) { env->SetDoubleArrayRegion(arr, 0, 2, out); return arr; }
    g_abort = false;
    llama_memory_t mem = llama_get_memory(g_ctx);
    llama_memory_clear(mem, true);
    g_cached.clear();

    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    std::vector<llama_token> toks;
    const llama_token bos = llama_vocab_bos(vocab);
    for (int i = 0; i < pp; i++) toks.push_back(i == 0 && bos != LLAMA_TOKEN_NULL ? bos : static_cast<llama_token>(100 + (i % 500)));

    common_batch batch(g_ctx);
    int64_t t0 = ggml_time_us();
    if (decode_range(batch, toks, 0, true) == 0) {
        out[0] = pp / ((ggml_time_us() - t0) / 1e6);
        llama_pos pos = pp;
        int64_t t1 = ggml_time_us();
        int done = 0;
        for (int i = 0; i < tg && !g_abort; i++) {
            batch.clear();
            batch.add(static_cast<llama_token>(100 + i), pos++, 0, true);
            if (llama_process(g_ctx, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) break;
            done++;
        }
        if (done > 0) out[1] = done / ((ggml_time_us() - t1) / 1e6);
    }
    llama_memory_clear(mem, true);
    env->SetDoubleArrayRegion(arr, 0, 2, out);
    return arr;
}

// Pembungkus aman: exception C++ dari llama.cpp tidak boleh membuat aplikasi crash.
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_gemmaagent_app_llm_LlamaNative_nativeGenerate(JNIEnv *env, jobject, jbyteArray jprompt,
                                                       jfloat temp, jfloat top_p, jint top_k, jfloat repeat_penalty,
                                                       jint max_tokens, jobject callback) {
    try {
        return generate_impl(env, jprompt, temp, top_p, top_k, repeat_penalty, max_tokens, callback);
    } catch (const std::exception &e) {
        LOGE("generate exception: %s", e.what());
    } catch (...) {
        LOGE("generate exception tidak dikenal");
    }
    if (g_ctx) llama_memory_clear(llama_get_memory(g_ctx), true);
    g_cached.clear();
    jlong res[6] = {0, 0, 0, 0, 0, -9};
    jlongArray arr = env->NewLongArray(6);
    env->SetLongArrayRegion(arr, 0, 6, res);
    return arr;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_gemmaagent_app_llm_LlamaNative_nativeBench(JNIEnv *env, jobject, jint pp, jint tg) {
    try {
        return bench_impl(env, pp, tg);
    } catch (...) {
        LOGE("bench exception");
    }
    jdouble out[2] = {0, 0};
    jdoubleArray arr = env->NewDoubleArray(2);
    env->SetDoubleArrayRegion(arr, 0, 2, out);
    return arr;
}
