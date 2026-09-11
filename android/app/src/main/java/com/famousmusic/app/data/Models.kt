package com.famousmusic.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================
// 与后端 API 对应的数据模型（api.famousmusic.asia）
// ============================================================

@Serializable
data class User(
    val id: Int,
    val email: String,
    val username: String,
    @SerialName("avatar_key") val avatarKey: String? = null,
    val title: String? = null,
    @SerialName("is_admin") val isAdmin: Int = 0,
    val banned: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Song(
    val id: Int,
    val title: String,
    val artist: String = "",
    val genre: String = "其他",
    @SerialName("cover_key") val coverKey: String? = null,
    @SerialName("audio_key") val audioKey: String = "",
    @SerialName("lyrics_key") val lyricsKey: String? = null,
    val duration: Int = 0,
    @SerialName("uploader_id") val uploaderId: Int,
    @SerialName("uploader_name") val uploaderName: String? = null,
    @SerialName("uploader_title") val uploaderTitle: String? = null,
    @SerialName("created_at") val createdAt: String = "",
) {
    /** 展示用歌手名（无 artist 时回退到上传者） */
    val displayArtist: String get() = artist.ifBlank { uploaderName ?: "未知" }
}

@Serializable
data class SongListResponse(
    val songs: List<Song> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 20,
)

@Serializable
data class SongDetailResponse(
    val song: Song,
    val favoriteCount: Int = 0,
    val favorited: Boolean = false,
)

@Serializable
data class FavoriteResponse(
    val success: Boolean = true,
    val favorite: Boolean = false,
    val favoriteCount: Int = 0,
)

@Serializable
data class Playlist(
    val id: Int,
    val name: String,
    @SerialName("user_id") val userId: Int = 0,
    @SerialName("owner_name") val ownerName: String? = null,
    @SerialName("song_count") val songCount: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class PlaylistListResponse(val playlists: List<Playlist> = emptyList())

@Serializable
data class PlaylistDetailResponse(
    val playlist: Playlist,
    val songs: List<Song> = emptyList(),
)

@Serializable
data class Comment(
    val id: Int,
    @SerialName("song_id") val songId: Int,
    @SerialName("user_id") val userId: Int,
    val username: String,
    val title: String? = null,
    val content: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class CommentListResponse(val comments: List<Comment> = emptyList())

@Serializable
data class CommentCreateResponse(val success: Boolean = true, val comment: Comment)

@Serializable
data class GenreCount(val genre: String, val count: Int)

@Serializable
data class GenreListResponse(val genres: List<GenreCount> = emptyList())

@Serializable
data class AuthResponse(val token: String, val user: User)

@Serializable
data class MeResponse(val user: User)

@Serializable
data class OkResponse(val success: Boolean = true, val message: String? = null)

@Serializable
data class CreateIdResponse(val success: Boolean = true, val id: Int)

@Serializable
data class PresignResponse(
    val success: Boolean = true,
    val id: String = "",
    @SerialName("audioKey") val audioKey: String = "",
    @SerialName("audioUrl") val audioUrl: String = "",
    @SerialName("coverKey") val coverKey: String? = null,
    @SerialName("coverUrl") val coverUrl: String? = null,
    @SerialName("lyricsKey") val lyricsKey: String? = null,
    @SerialName("lyricsUrl") val lyricsUrl: String? = null,
)

@Serializable
data class CompleteResponse(val success: Boolean = true, val id: Int = 0)

// ---------- 管理员 ----------

@Serializable
data class AdminUser(
    val id: Int,
    val email: String,
    val username: String,
    @SerialName("avatar_key") val avatarKey: String? = null,
    val title: String? = null,
    @SerialName("is_admin") val isAdmin: Int = 0,
    val banned: Int = 0,
    @SerialName("songs_count") val songsCount: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class AdminUserListResponse(val users: List<AdminUser> = emptyList())

@Serializable
data class AdminUpdateUserRequest(
    val username: String? = null,
    val title: String? = null,
    @SerialName("is_admin") val isAdmin: Int? = null,
    val banned: Int? = null,
)

// ---------- 请求体 ----------

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
    val code: String,
)

@Serializable
data class SendCodeRequest(val email: String, val purpose: String)

@Serializable
data class ResetPasswordRequest(
    val email: String,
    val code: String,
    @SerialName("newPassword") val newPassword: String,
)

@Serializable
data class PresignRequest(
    val title: String,
    val artist: String,
    val genre: String,
    val duration: Int,
    val audioName: String,
    val coverName: String? = null,
    val lyricsName: String? = null,
)

@Serializable
data class CompleteRequest(
    val title: String,
    val artist: String,
    val genre: String,
    val duration: Int,
    val audioKey: String,
    val coverKey: String? = null,
    val lyricsKey: String? = null,
)

@Serializable
data class CreatePlaylistRequest(val name: String)

@Serializable
data class AddSongRequest(@SerialName("songId") val songId: Int)

@Serializable
data class CreateCommentRequest(@SerialName("songId") val songId: Int, val content: String)

@Serializable
data class AvatarCompleteRequest(val key: String)

@Serializable
data class PresignNameRequest(val name: String)

/** 后端错误响应体 */
@Serializable
data class ErrorBody(val error: String? = null)
