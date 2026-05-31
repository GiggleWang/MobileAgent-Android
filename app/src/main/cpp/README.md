# On-device VLM native engine (llama.cpp + libmtmd)

This directory builds `libvlmjni.so`, the JNI bridge that runs MiniCPM-V 4.6 on-device
for the "自带模型 (Built-in / on-device)" provider. Kotlin side: `api/LlamaVlmEngine.kt`,
`api/LocalVlmClient.kt`.

## One-time setup: add the llama.cpp submodule

The build expects llama.cpp source at `app/src/main/cpp/llama.cpp`. It is NOT vendored;
add it as a git submodule pinned to a build that supports MiniCPM-V 4.6 (merged 2026-05-06,
commit `2496f9c`, release **≥ b9049**):

```bash
git submodule add https://github.com/ggml-org/llama.cpp.git app/src/main/cpp/llama.cpp
cd app/src/main/cpp/llama.cpp
git checkout b9049        # or any newer tag/commit
cd -
git submodule update --init --recursive
```

After cloning a fresh checkout of this repo, run `git submodule update --init --recursive`.

## Build

The native build is wired via `externalNativeBuild` in `app/build.gradle.kts`
(`ndkVersion`, `abiFilters = ["arm64-v8a"]`, CMake at `src/main/cpp/CMakeLists.txt`).
Just build the app:

```bash
./gradlew :app:assembleDebug
```

CMake forces `LLAMA_BUILD_COMMON=ON` + `LLAMA_BUILD_TOOLS=ON` (required so `libmtmd` is
built — it lives under `llama.cpp/tools/`) and links `mtmd` (which pulls in `llama`/`ggml`).
Output libs bundled in the APK: `libvlmjni.so`, `libllama.so`, `libmtmd.so`,
`libggml*.so` (arm64-v8a only).

Verify 16 KB page alignment (Android 15+):
```bash
llvm-readelf --program-headers app/build/intermediates/.../arm64-v8a/libvlmjni.so | grep LOAD
# Align should be 0x4000
```

## If the build breaks on a symbol

libmtmd's API moves fast. Re-verify these against the checked-out headers and adjust
`vlm_jni.cpp`:
- `mtmd_default_marker()` / `media_marker` field — `tools/mtmd/mtmd.h`
- `mtmd_helper_bitmap_init_from_buf(mtmd_context*, buf, len)` arg order — `tools/mtmd/mtmd-helper.h`
- `mtmd_helper_eval_chunks(...)` signature — `tools/mtmd/mtmd-helper.h`
- `llama_memory_clear(llama_get_memory(ctx), true)` (renamed from `llama_kv_self_clear`) — `include/llama.h`
- `llama_sampler_init_penalties(last_n, repeat, freq, present)` arg count — `include/llama.h`

## Model files

Downloaded at runtime by the in-app button (`data/ModelDownloadManager.kt`,
`api/ModelManifest.kt`) into `<external-files>/models/`:
- `MiniCPM-V-4.6-Q4_K_M.gguf` (~529 MB)
- `mmproj-MiniCPM-V-4.6-Q8_0.gguf` (~728 MB)

Sanity-check the model + prompt on desktop before trusting the JNI path:
```bash
llama-mtmd-cli -m MiniCPM-V-4.6-Q4_K_M.gguf --mmproj mmproj-MiniCPM-V-4.6-Q8_0.gguf \
  -c 8192 --temp 0.7 --top-p 0.8 --top-k 100 --repeat-penalty 1.05 --reasoning off \
  --image shot.jpg -p "What is in the image?"
```
