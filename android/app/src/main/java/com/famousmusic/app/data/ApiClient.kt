package com.famousmusic.app.data

import com.famousmusic.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/** API 调用异常（message 可直接展示给用户） */
class ApiException(message: String, val status: Int = 0) : Exception(message)

@Serializable
data class AdminUpdateResponse(val success: Boolean = true, val user: AdminUser)

@Serializable
data class AvatarPresignResponse(
    val success: Boolean = true,
    val key: String = "",
    val url: String = "",
)

/**
 * 后端 API 客户端：统一注入 JWT、解析 JSON、抛出可读错误。
 * 对应 api.famousmusic.asia（与网站前端同一套接口）。
 */
object ApiClient {
    val BASE: String = BuildConfig.API_BASE
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val token = TokenStore.token
                val req = if (token.isNullOrBlank()) {
                    chain.request()
                } else {
                    chain.request().newBuilder().header("Authorization", "Bearer $token").build()
                }
                chain.proceed(req)
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
                }
            )
            .build()
    }

    // ---------- 底层请求 ----------

    private suspend fun exec(request: Request): String = withContext(Dispatchers.IO) {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw ApiException("网络连接失败，请检查网络后重试", 0)
        }
        response.use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = runCatching {
                    AppJson.instance.decodeFromString(ErrorBody.serializer(), text).error
                }.getOrNull()
                throw ApiException(msg ?: "请求失败 (${res.code})", res.code)
            }
            text
        }
    }

    private suspend fun getRaw(path: String): String =
        exec(Request.Builder().url(BASE + path).get().build())

    private suspend fun sendRaw(method: String, path: String, bodyJson: String? = null): String {
        val builder = Request.Builder().url(BASE + path)
        when (method) {
            "POST" -> builder.post((bodyJson ?: "").toRequestBody(JSON_MEDIA))
            "PATCH" -> builder.patch((bodyJson ?: "").toRequestBody(JSON_MEDIA))
            "PUT" -> builder.put((bodyJson ?: "").toRequestBody(JSON_MEDIA))
            "DELETE" -> if (bodyJson == null) builder.delete() else builder.delete(bodyJson.toRequestBody(JSON_MEDIA))
        }
        return exec(builder.build())
    }

    private suspend fun <T> getDecoded(path: String, d: DeserializationStrategy<T>): T =
        AppJson.instance.decodeFromString(d, getRaw(path))

    private suspend fun <B, T> sendDecoded(
        method: String,
        path: String,
        body: B,
        s: SerializationStrategy<B>,
        d: DeserializationStrategy<T>,
    ): T {
        val payload = AppJson.instance.encodeToString(s, body)
        return AppJson.instance.decodeFromString(d, sendRaw(method, path, payload))
    }

    private suspend fun <T> decodeOnly(
        method: String,
        path: String,
        d: DeserializationStrategy<T>,
    ): T = AppJson.instance.decodeFromString(d, sendRaw(method, path, null))

    /** 直传文件到预签名 URL（OSS） */
    suspend fun uploadToPresigned(url: String, bytes: ByteArray, mimeType: String): Unit =
        withContext(Dispatchers.IO) {
            val res = try {
                http.newCall(
                    Request.Builder()
                        .url(url)
                        .put(bytes.toRequestBody(mimeType.toMediaType()))
                        .build()
                ).execute()
            } catch (e: IOException) {
                throw ApiException("上传到存储失败，请检查网络", 0)
            }
            res.use {
                if (!it.isSuccessful) throw ApiException("上传到存储失败(${it.code})", it.code)
            }
        }

    // ---------- 认证 ----------

    suspend fun login(email: String, password: String): AuthResponse =
        sendDecoded("POST", "/auth/login", LoginRequest(email, password), LoginRequest.serializer(), AuthResponse.serializer())

    suspend fun register(email: String, username: String, password: String, code: String): AuthResponse =
        sendDecoded(
            "POST", "/auth/register",
            RegisterRequest(email, username, password, code),
            RegisterRequest.serializer(), AuthResponse.serializer()
        )

    suspend fun sendCode(email: String, purpose: String): OkResponse =
        sendDecoded("POST", "/auth/send-code", SendCodeRequest(email, purpose), SendCodeRequest.serializer(), OkResponse.serializer())

    suspend fun forgot(email: String): OkResponse =
        sendDecoded("POST", "/auth/forgot", SendCodeRequest(email, "reset"), SendCodeRequest.serializer(), OkResponse.serializer())

    suspend fun resetPassword(email: String, code: String, newPassword: String): OkResponse =
        sendDecoded(
            "POST", "/auth/reset-password",
            ResetPasswordRequest(email, code, newPassword),
            ResetPasswordRequest.serializer(), OkResponse.serializer()
        )

    suspend fun me(): MeResponse = getDecoded("/auth/me", MeResponse.serializer())

    // ---------- 头像 ----------

    suspend fun avatarPresign(name: String): AvatarPresignResponse =
        sendDecoded("POST", "/users/avatar-presign", PresignNameRequest(name), PresignNameRequest.serializer(), AvatarPresignResponse.serializer())

    suspend fun avatarComplete(key: String): OkResponse =
        sendDecoded("POST", "/users/avatar-complete", AvatarCompleteRequest(key), AvatarCompleteRequest.serializer(), OkResponse.serializer())

    // ---------- 歌曲 ----------

    suspend fun listSongs(q: String? = null, genre: String? = null, page: Int = 1, limit: Int = 20): SongListResponse {
        val params = buildList {
            if (!q.isNullOrBlank()) add("q=" + urlEncode(q))
            if (!genre.isNullOrBlank()) add("genre=" + urlEncode(genre))
            add("page=$page")
            add("limit=$limit")
        }
        return getDecoded("/songs?" + params.joinToString("&"), SongListResponse.serializer())
    }

    suspend fun songDetail(id: Int): SongDetailResponse = getDecoded("/songs/$id", SongDetailResponse.serializer())

    suspend fun lyrics(songId: Int): String = getRaw("/songs/$songId/lyrics")

    suspend fun genres(): GenreListResponse = getDecoded("/songs/genres", GenreListResponse.serializer())

    suspend fun favorites(): SongListResponse = getDecoded("/songs/favorites", SongListResponse.serializer())

    suspend fun favorite(id: Int): FavoriteResponse =
        decodeOnly("POST", "/songs/$id/favorite", FavoriteResponse.serializer())

    suspend fun unfavorite(id: Int): FavoriteResponse =
        decodeOnly("DELETE", "/songs/$id/favorite", FavoriteResponse.serializer())

    suspend fun deleteSong(id: Int): OkResponse =
        decodeOnly("DELETE", "/songs/$id", OkResponse.serializer())

    suspend fun presign(req: PresignRequest): PresignResponse =
        sendDecoded("POST", "/songs/presign", req, PresignRequest.serializer(), PresignResponse.serializer())

    suspend fun complete(req: CompleteRequest): CompleteResponse =
        sendDecoded("POST", "/songs/complete", req, CompleteRequest.serializer(), CompleteResponse.serializer())

    // ---------- 歌单 ----------

    suspend fun myPlaylists(): PlaylistListResponse = getDecoded("/playlists/mine", PlaylistListResponse.serializer())

    suspend fun createPlaylist(name: String): CreateIdResponse =
        sendDecoded("POST", "/playlists", CreatePlaylistRequest(name), CreatePlaylistRequest.serializer(), CreateIdResponse.serializer())

    suspend fun playlistDetail(id: Int): PlaylistDetailResponse =
        getDecoded("/playlists/$id", PlaylistDetailResponse.serializer())

    suspend fun addSongToPlaylist(playlistId: Int, songId: Int): OkResponse =
        sendDecoded(
            "POST", "/playlists/$playlistId/songs",
            AddSongRequest(songId), AddSongRequest.serializer(), OkResponse.serializer()
        )

    suspend fun removeSongFromPlaylist(playlistId: Int, songId: Int): OkResponse =
        decodeOnly("DELETE", "/playlists/$playlistId/songs/$songId", OkResponse.serializer())

    suspend fun deletePlaylist(id: Int): OkResponse =
        decodeOnly("DELETE", "/playlists/$id", OkResponse.serializer())

    // ---------- 评论 ----------

    suspend fun comments(songId: Int): CommentListResponse =
        getDecoded("/comments/song/$songId", CommentListResponse.serializer())

    suspend fun createComment(songId: Int, content: String): CommentCreateResponse =
        sendDecoded(
            "POST", "/comments",
            CreateCommentRequest(songId, content), CreateCommentRequest.serializer(), CommentCreateResponse.serializer()
        )

    suspend fun deleteComment(id: Int): OkResponse =
        decodeOnly("DELETE", "/comments/$id", OkResponse.serializer())

    // ---------- 管理员 ----------

    suspend fun adminUsers(): AdminUserListResponse = getDecoded("/admin/users", AdminUserListResponse.serializer())

    suspend fun adminUpdateUser(id: Int, req: AdminUpdateUserRequest): AdminUpdateResponse =
        sendDecoded(
            "PATCH", "/admin/users/$id", req,
            AdminUpdateUserRequest.serializer(), AdminUpdateResponse.serializer()
        )

    suspend fun adminDeleteUser(id: Int): OkResponse =
        decodeOnly("DELETE", "/admin/users/$id", OkResponse.serializer())

    // ---------- 资源地址 ----------

    fun streamUrl(songId: Int): String = "$BASE/songs/$songId/stream"

    fun lyricsUrl(songId: Int): String = "$BASE/songs/$songId/lyrics"

    fun coverUrl(song: Song): String? =
        if (song.coverKey.isNullOrBlank()) null else "$BASE/songs/${song.id}/cover"

    fun avatarUrl(user: User): String =
        "$BASE/users/${user.id}/avatar?v=" + urlEncode(user.avatarKey ?: "default")

    fun avatarUrl(userId: Int, avatarKey: String?): String =
        "$BASE/users/$userId/avatar?v=" + urlEncode(avatarKey ?: "default")

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
