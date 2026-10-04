package com.tgzjdv.music.data

import com.tgzjdv.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * UAPI 随机图片接口客户端（uapis.cn）。
 *
 * 文档：https://uapis.cn/docs/api-reference/get-random-image
 *
 * - **完整 API 地址**照文档整条使用，不取相对路径、不省略 `/api/v1` 版本前缀：
 *   `https://uapis.cn/api/v1/random/image`
 * - **鉴权**：`Authorization: Bearer <KEY>`，密钥以 `uapi-` 开头。
 *   密钥来自 [UapiKeyStore]（应用内设置）或 `BuildConfig.UAPI_KEY`（android/uapi.properties，
 *   已 gitignore）。**不硬编码、不拼进 URL。**
 * - **成功**：302 重定向到图片，跟随重定向后返回 `image/jpeg` 二进制。
 * - **错误**：`404 {"code":"NOT_FOUND"}`、`500 {"code":"INTERNAL_SERVER_ERROR"}`。
 */
object UapiClient {

    /** 文档给出的基址 */
    const val BASE = "https://uapis.cn/api/v1"

    /** 文档标注的「完整 API 地址」 */
    const val RANDOM_IMAGE_URL = "$BASE/random/image"

    /** `category` 可选值（严格照文档，不自行增补） */
    val CATEGORIES: List<String> = listOf(
        "acg", "landscape", "anime", "pc_wallpaper", "mobile_wallpaper",
        "general_anime", "ai_drawing", "bq", "furry",
    )

    /** `type` 可选值（严格照文档） */
    val TYPES: List<String> = listOf(
        "pc", "mb", "eciyuan", "ikun", "4k", "s4k", "z4k", "szs8k", "xiongmao", "maomao", "waiguoren",
    )

    /**
     * 文档明确：`type` **仅 UapiPro 服务器图片支持**，
     * 即只对 acg / bq / furry 三类有效（外部图床与 anime 混合类别不支持）。
     */
    val TYPE_CATEGORIES: Set<String> = setOf("acg", "bq", "furry")

    /** 类别中文名，仅用于界面展示 */
    val CATEGORY_LABELS: Map<String, String> = mapOf(
        "acg" to "二次元动漫",
        "landscape" to "风景图",
        "anime" to "混合动漫",
        "pc_wallpaper" to "电脑壁纸",
        "mobile_wallpaper" to "手机壁纸",
        "general_anime" to "动漫图",
        "ai_drawing" to "AI 绘画",
        "bq" to "表情包 / 趣图",
        "furry" to "福瑞",
    )

    @Serializable
    private data class UapiErrorBody(val code: String = "", val message: String = "")

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            // 接口用 302 把图片地址给你，必须跟随重定向才能拿到图片二进制
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** 生效的密钥：应用内设置的优先，其次构建期注入的 */
    fun effectiveKey(): String = UapiKeyStore.key().ifBlank { BuildConfig.UAPI_KEY }

    fun hasKey(): Boolean = effectiveKey().isNotBlank()

    /**
     * 取一张随机图片。
     *
     * @param category 主类别，null = 全局随机（文档：不含 ikun 与 ai_drawing）
     * @param type 子类别，仅 [TYPE_CATEGORIES] 里的类别支持；传 null 表示不筛
     * @return 图片二进制（image/jpeg）
     * @throws ApiException 参数非法 / 无密钥 / 网络失败 / 非 2xx / 限流 / 返回的不是图片
     */
    suspend fun randomImage(category: String? = null, type: String? = null): ByteArray =
        withContext(Dispatchers.IO) {
            // ---------- 参数校验（照文档：不在可选值内的一律拒绝，不猜测） ----------
            if (category != null && category !in CATEGORIES) {
                throw ApiException("不支持的 category：$category")
            }
            if (type != null) {
                if (type !in TYPES) throw ApiException("不支持的 type：$type")
                if (category == null || category !in TYPE_CATEGORIES) {
                    throw ApiException(
                        "type 仅在 ${TYPE_CATEGORIES.joinToString(" / ")} 类别下受支持（文档：外部图床与 anime 混合类别不支持 type）",
                    )
                }
            }

            val key = effectiveKey()
            if (key.isBlank()) {
                throw ApiException("未配置 UAPI 密钥：请在「设置 → 自定义背景」里填入以 uapi- 开头的密钥")
            }

            val url = buildString {
                append(RANDOM_IMAGE_URL)
                val params = buildList {
                    if (category != null) add("category" to category)
                    if (type != null) add("type" to type)
                }
                if (params.isNotEmpty()) {
                    append('?')
                    append(params.joinToString("&") { "${it.first}=${it.second}" })
                }
            }

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $key") // 密钥只放请求头，不进 URL
                .header("Accept", "image/jpeg,image/*;q=0.9,*/*;q=0.5")
                .get()
                .build()

            val response = try {
                http.newCall(request).execute()
            } catch (e: IOException) {
                throw ApiException("网络连接失败，请检查网络后重试", 0)
            }

            response.use { res ->
                if (res.isSuccessful) {
                    val contentType = res.body?.contentType()?.toString().orEmpty()
                    val bytes = res.body?.bytes()
                    if (bytes == null || bytes.isEmpty()) {
                        throw ApiException("接口返回了空内容", res.code)
                    }
                    // 接口正常时应为 image/*；若拿到 JSON 说明服务端改了行为，按错误处理而不是把 JSON 当图片
                    if (!contentType.startsWith("image/")) {
                        throw ApiException(
                            parseError(bytes.toString(Charsets.UTF_8))
                                ?: "接口返回的不是图片（Content-Type: $contentType）",
                            res.code,
                        )
                    }
                    return@withContext bytes
                }

                // ---------- 错误码（文档只列了 404 / 500，其余按通用语义给出可读文案） ----------
                val text = runCatching { res.body?.string().orEmpty() }.getOrNull().orEmpty()
                val fromBody = parseError(text)
                val message = fromBody ?: when (res.code) {
                    400 -> "请求参数有误"
                    401, 403 -> "UAPI 密钥无效或未授权，请检查「设置 → 自定义背景」里的密钥"
                    404 -> "未找到指定类别的图片"
                    429 -> "请求过于频繁，已被限流，请稍后重试"
                    in 500..599 -> "UAPI 服务器内部错误，请稍后重试"
                    else -> "请求失败 (${res.code})"
                }
                throw ApiException(message, res.code)
            }
        }

    /** 解析文档里的 `{"code":..., "message":...}` 错误体 */
    private fun parseError(text: String): String? {
        if (text.isBlank()) return null
        return runCatching {
            val body = AppJson.instance.decodeFromString(UapiErrorBody.serializer(), text)
            body.message.ifBlank { body.code }.ifBlank { null }
        }.getOrNull()
    }
}
