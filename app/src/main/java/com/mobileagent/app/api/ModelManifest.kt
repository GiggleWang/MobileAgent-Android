package com.mobileagent.app.api

object ModelManifest {

    data class ModelFile(
        val fileName: String,
        val url: String,
        val sizeBytes: Long,
        val sha256: String? = null
    )

    data class ModelEntry(
        val id: String,
        val displayName: String,
        val sizeLabel: String,
        val description: String,
        val recommended: Boolean = false,
        val base: ModelFile,
        val mmproj: ModelFile
    ) {
        val files: List<ModelFile> get() = listOf(base, mmproj)
        val totalBytes: Long get() = base.sizeBytes + mmproj.sizeBytes
    }

    private const val MINICPM_REPO = "https://huggingface.co/ggml-org/MiniCPM-V-4.6-GGUF/resolve/main"
    private const val QWEN3_REPO   = "https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct-GGUF/resolve/main"
    private const val QWEN2_REPO   = "https://huggingface.co/ggml-org/Qwen2-VL-2B-Instruct-GGUF/resolve/main"
    private const val SMOL_REPO    = "https://huggingface.co/ggml-org/SmolVLM2-2.2B-Instruct-GGUF/resolve/main"

    val QWEN3_VL_2B = ModelEntry(
        id          = "qwen3-vl-2b",
        displayName = "Qwen3-VL 2B",
        sizeLabel   = "~1.4 GB",
        description = "Q4_K_M · Best GUI agent",
        recommended = true,
        base = ModelFile(
            fileName  = "Qwen3VL-2B-Instruct-Q4_K_M.gguf",
            url       = "$QWEN3_REPO/Qwen3VL-2B-Instruct-Q4_K_M.gguf",
            sizeBytes = 1_107_409_952L
        ),
        mmproj = ModelFile(
            fileName  = "mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf",
            url       = "$QWEN3_REPO/mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf",
            sizeBytes = 445_053_216L
        )
    )

    val QWEN2_VL_2B = ModelEntry(
        id          = "qwen2-vl-2b",
        displayName = "Qwen2-VL 2B",
        sizeLabel   = "~1.6 GB",
        description = "Q4_K_M · Stable",
        base = ModelFile(
            fileName  = "Qwen2-VL-2B-Instruct-Q4_K_M.gguf",
            url       = "$QWEN2_REPO/Qwen2-VL-2B-Instruct-Q4_K_M.gguf",
            sizeBytes = 986_046_944L
        ),
        mmproj = ModelFile(
            fileName  = "mmproj-Qwen2-VL-2B-Instruct-Q8_0.gguf",
            url       = "$QWEN2_REPO/mmproj-Qwen2-VL-2B-Instruct-Q8_0.gguf",
            sizeBytes = 709_883_360L
        )
    )

    val MINICPM_V_46 = ModelEntry(
        id          = "minicpm-v-4.6",
        displayName = "MiniCPM-V 4.6",
        sizeLabel   = "~1.2 GB",
        description = "Q4_K_M · OpenBMB",
        base = ModelFile(
            fileName  = "MiniCPM-V-4.6-Q4_K_M.gguf",
            url       = "$MINICPM_REPO/MiniCPM-V-4.6-Q4_K_M.gguf",
            sizeBytes = 529_101_536L,
            sha256    = "b1a5aa76b5ef039c2e579272ea33d4bbed7e79b49bb3ff1efdb23316d6af5199"
        ),
        mmproj = ModelFile(
            fileName  = "mmproj-MiniCPM-V-4.6-Q8_0.gguf",
            url       = "$MINICPM_REPO/mmproj-MiniCPM-V-4.6-Q8_0.gguf",
            sizeBytes = 727_954_528L,
            sha256    = "3d8249cdd0e1cb699644eb021fbcc04320aad89fa5dc9234ef94db0846556581"
        )
    )

    val SMOLVLM2_2B = ModelEntry(
        id          = "smolvlm2-2.2b",
        displayName = "SmolVLM2 2.2B",
        sizeLabel   = "~1.6 GB",
        description = "Q4_K_M · HuggingFace",
        base = ModelFile(
            fileName  = "SmolVLM2-2.2B-Instruct-Q4_K_M.gguf",
            url       = "$SMOL_REPO/SmolVLM2-2.2B-Instruct-Q4_K_M.gguf",
            sizeBytes = 1_112_602_656L
        ),
        mmproj = ModelFile(
            fileName  = "mmproj-SmolVLM2-2.2B-Instruct-Q8_0.gguf",
            url       = "$SMOL_REPO/mmproj-SmolVLM2-2.2B-Instruct-Q8_0.gguf",
            sizeBytes = 592_523_200L
        )
    )

    val ALL: List<ModelEntry> = listOf(QWEN3_VL_2B, QWEN2_VL_2B, MINICPM_V_46, SMOLVLM2_2B)

    const val DEFAULT_ID: String = "qwen3-vl-2b"

    fun findById(id: String): ModelEntry? = ALL.find { it.id == id }
}
