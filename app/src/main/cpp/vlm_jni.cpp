// JNI bridge: on-device vision-language inference via llama.cpp + libmtmd.
//
// Exposes three native methods to com.mobileagent.app.api.LlamaVlmEngine:
//   nativeInit(modelPath, mmprojPath, nThreads, nCtx) -> handle
//   nativePredict(handle, prompt, images[][], maxTokens) -> String
//   nativeFree(handle)
//
// The handle owns a cached { llama_model, llama_context, mtmd_context } so the
// (expensive) model + projector load happens once and is reused across calls.
// Each predict clears the KV cache so calls are stateless, matching VlmApiClient.
//
// NOTE: libmtmd's API moves fast. If the build fails on a symbol, verify against
// the checked-out headers: tools/mtmd/mtmd.h, tools/mtmd/mtmd-helper.h, include/llama.h.
// Symbols most likely to drift: mtmd_default_marker, mtmd_helper_bitmap_init_from_buf
// (mtmd_context* is the first arg), llama_memory_clear / llama_get_memory.

#include <jni.h>
#include <android/log.h>
#include <chrono>
#include <string>
#include <vector>

#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define LOG_TAG "vlmjni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct VlmContext {
    llama_model *   model = nullptr;
    llama_context * lctx  = nullptr;
    mtmd_context *  mctx  = nullptr;
    int             n_threads = 4;
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_mobileagent_app_api_LlamaVlmEngine_nativeInit(
        JNIEnv * env, jobject /*thiz*/,
        jstring jModelPath, jstring jMmprojPath, jint nThreads, jint nCtx) {

    const char * modelPath  = env->GetStringUTFChars(jModelPath,  nullptr);
    const char * mmprojPath = env->GetStringUTFChars(jMmprojPath, nullptr);

    auto cleanup_strings = [&]() {
        env->ReleaseStringUTFChars(jModelPath,  modelPath);
        env->ReleaseStringUTFChars(jMmprojPath, mmprojPath);
    };

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU only

    llama_model * model = llama_model_load_from_file(modelPath, mparams);
    if (!model) {
        LOGE("failed to load model: %s", modelPath);
        cleanup_strings();
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx           = (uint32_t) nCtx;
    cparams.n_batch         = 512;
    cparams.n_threads       = nThreads;
    cparams.n_threads_batch = nThreads;

    llama_context * lctx = llama_init_from_model(model, cparams);
    if (!lctx) {
        LOGE("failed to create llama context");
        llama_model_free(model);
        cleanup_strings();
        return 0;
    }

    mtmd_context_params mp = mtmd_context_params_default();
    mp.use_gpu       = false;
    mp.n_threads     = nThreads;
    mp.print_timings = false;

    mtmd_context * mctx = mtmd_init_from_file(mmprojPath, model, mp);
    if (!mctx) {
        LOGE("failed to init mtmd from: %s", mmprojPath);
        llama_free(lctx);
        llama_model_free(model);
        cleanup_strings();
        return 0;
    }

    cleanup_strings();

    auto * vlm = new VlmContext();
    vlm->model     = model;
    vlm->lctx      = lctx;
    vlm->mctx      = mctx;
    vlm->n_threads = nThreads;
    LOGI("vlm engine ready (n_ctx=%d, threads=%d)", nCtx, nThreads);
    return reinterpret_cast<jlong>(vlm);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileagent_app_api_LlamaVlmEngine_nativePredict(
        JNIEnv * env, jobject /*thiz*/,
        jlong handle, jstring jPrompt, jobjectArray jImages, jint maxTokens) {

    auto * vlm = reinterpret_cast<VlmContext *>(handle);
    if (!vlm) return env->NewStringUTF("");

    // Stateless per call: drop any KV state from a previous prediction.
    llama_memory_clear(llama_get_memory(vlm->lctx), true);

    const char * promptC = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(promptC ? promptC : "");
    env->ReleaseStringUTFChars(jPrompt, promptC);

    // Decode the JPEG/PNG byte arrays into mtmd bitmaps (stb_image under the hood).
    std::vector<mtmd_bitmap *> bitmaps;
    const int nImg = jImages ? env->GetArrayLength(jImages) : 0;
    for (int i = 0; i < nImg; i++) {
        auto arr = (jbyteArray) env->GetObjectArrayElement(jImages, i);
        if (!arr) continue;
        jsize len   = env->GetArrayLength(arr);
        jbyte * buf = env->GetByteArrayElements(arr, nullptr);
        mtmd_bitmap * bmp = mtmd_helper_bitmap_init_from_buf(
                vlm->mctx, (const unsigned char *) buf, (size_t) len);
        env->ReleaseByteArrayElements(arr, buf, JNI_ABORT);
        env->DeleteLocalRef(arr);
        if (bmp) bitmaps.push_back(bmp);
        else     LOGE("failed to decode image %d", i);
    }

    // Ensure one media marker per image; prepend any that are missing.
    const std::string marker = mtmd_default_marker(); // "<__media__>"
    size_t markerCount = 0;
    for (size_t pos = 0; (pos = prompt.find(marker, pos)) != std::string::npos; pos += marker.size()) {
        markerCount++;
    }
    if (markerCount < bitmaps.size()) {
        std::string prefix;
        for (size_t i = markerCount; i < bitmaps.size(); i++) prefix += marker + "\n";
        prompt = prefix + prompt;
    }

    mtmd_input_text text;
    text.text          = prompt.c_str();
    text.add_special   = true;
    text.parse_special = true;

    mtmd_input_chunks * chunks = mtmd_input_chunks_init();
    int32_t rc = mtmd_tokenize(vlm->mctx, chunks, &text,
                               (const mtmd_bitmap **) bitmaps.data(), bitmaps.size());
    if (rc != 0) {
        LOGE("mtmd_tokenize failed: %d", rc);
        mtmd_input_chunks_free(chunks);
        for (auto * b : bitmaps) mtmd_bitmap_free(b);
        return env->NewStringUTF("");
    }

    // Prefill: encode image(s) + text and decode into the KV cache.
    llama_pos n_past = 0;
    int32_t ev = mtmd_helper_eval_chunks(vlm->mctx, vlm->lctx, chunks,
                                         /*n_past*/ 0, /*seq_id*/ 0,
                                         /*n_batch*/ 512, /*logits_last*/ true,
                                         &n_past);
    mtmd_input_chunks_free(chunks);
    for (auto * b : bitmaps) mtmd_bitmap_free(b);
    if (ev != 0) {
        LOGE("mtmd_helper_eval_chunks failed: %d", ev);
        return env->NewStringUTF("");
    }

    // Sampler chain: penalties -> top_k -> top_p -> temp -> dist.
    const llama_vocab * vocab = llama_model_get_vocab(vlm->model);
    llama_sampler * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(64, 1.05f, 0.0f, 0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(100));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.8f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string result;
    llama_batch batch = llama_batch_init(1, 0, 1);
    int generated = 0;
    while (generated < maxTokens) {
        const llama_token id = llama_sampler_sample(smpl, vlm->lctx, -1);
        if (llama_vocab_is_eog(vocab, id)) break;

        char piece[256];
        const int n = llama_token_to_piece(vocab, id, piece, sizeof(piece), 0, true);
        if (n > 0) result.append(piece, n);

        // Feed the sampled token back to obtain the next logits.
        batch.n_tokens     = 1;
        batch.token[0]     = id;
        batch.pos[0]       = n_past++;
        batch.n_seq_id[0]  = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0]    = 1;
        if (llama_decode(vlm->lctx, batch) != 0) {
            LOGE("llama_decode failed during generation");
            break;
        }
        generated++;
    }

    llama_batch_free(batch);
    llama_sampler_free(smpl);
    return env->NewStringUTF(result.c_str());
}

// Text-only latency benchmark. Returns a long[3] = { ttftMicros, decodeMicros, nTokens }:
//   ttftMicros   = time from start through prefill until the FIRST token is sampled (TTFT)
//   decodeMicros = total time to produce the remaining (nTokens-1) tokens
//   nTokens      = tokens generated
// Inter-token latency = decodeMicros / (nTokens - 1). EOG is ignored so a fixed number of
// tokens is always produced, keeping the average stable.
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mobileagent_app_api_LlamaVlmEngine_nativeBenchmark(
        JNIEnv * env, jobject /*thiz*/,
        jlong handle, jstring jPrompt, jint maxTokens) {
    using clock = std::chrono::steady_clock;
    auto makeResult = [&](jlong a, jlong b, jlong c) -> jlongArray {
        jlong arr[3] = {a, b, c};
        jlongArray r = env->NewLongArray(3);
        env->SetLongArrayRegion(r, 0, 3, arr);
        return r;
    };

    auto * vlm = reinterpret_cast<VlmContext *>(handle);
    if (!vlm) return makeResult(0, 0, 0);

    llama_memory_clear(llama_get_memory(vlm->lctx), true);

    const char * pc = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(pc ? pc : "");
    env->ReleaseStringUTFChars(jPrompt, pc);

    const auto t0 = clock::now();

    mtmd_input_text text;
    text.text          = prompt.c_str();
    text.add_special   = true;
    text.parse_special = true;

    mtmd_input_chunks * chunks = mtmd_input_chunks_init();
    if (mtmd_tokenize(vlm->mctx, chunks, &text, nullptr, 0) != 0) {
        mtmd_input_chunks_free(chunks);
        return makeResult(0, 0, 0);
    }
    llama_pos n_past = 0;
    int32_t ev = mtmd_helper_eval_chunks(vlm->mctx, vlm->lctx, chunks, 0, 0, 512, true, &n_past);
    mtmd_input_chunks_free(chunks);
    if (ev != 0) return makeResult(0, 0, 0);

    const llama_vocab * vocab = llama_model_get_vocab(vlm->model);
    llama_sampler * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    llama_batch batch = llama_batch_init(1, 0, 1);
    jlong ttftUs = 0, decodeUs = 0;
    int generated = 0;
    clock::time_point tPrev = t0;
    while (generated < maxTokens) {
        llama_token id = llama_sampler_sample(smpl, vlm->lctx, -1);
        auto now = clock::now();
        if (generated == 0) {
            ttftUs = (jlong) std::chrono::duration_cast<std::chrono::microseconds>(now - t0).count();
        } else {
            decodeUs += (jlong) std::chrono::duration_cast<std::chrono::microseconds>(now - tPrev).count();
        }
        tPrev = now;
        generated++;
        batch.n_tokens     = 1;
        batch.token[0]     = id;
        batch.pos[0]       = n_past++;
        batch.n_seq_id[0]  = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0]    = 1;
        if (llama_decode(vlm->lctx, batch) != 0) break;
    }
    llama_batch_free(batch);
    llama_sampler_free(smpl);
    return makeResult(ttftUs, decodeUs, (jlong) generated);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mobileagent_app_api_LlamaVlmEngine_nativeFree(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    auto * vlm = reinterpret_cast<VlmContext *>(handle);
    if (!vlm) return;
    if (vlm->mctx)  mtmd_free(vlm->mctx);
    if (vlm->lctx)  llama_free(vlm->lctx);
    if (vlm->model) llama_model_free(vlm->model);
    delete vlm;
}
