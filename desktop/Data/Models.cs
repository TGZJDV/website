using System.Text.Json.Serialization;

namespace FamousMusic.Desktop.Data;

// ============================================================
// 与后端 API（api.famousmusic.asia）一一对应的数据模型。
// 字段名严格照抄 Android 端 Models.kt，不做猜测。
// ============================================================

public sealed class User
{
    [JsonPropertyName("id")] public int Id { get; set; }
    [JsonPropertyName("email")] public string Email { get; set; } = "";
    [JsonPropertyName("username")] public string Username { get; set; } = "";
    [JsonPropertyName("avatar_key")] public string? AvatarKey { get; set; }
    [JsonPropertyName("title")] public string? Title { get; set; }
    [JsonPropertyName("is_admin")] public int IsAdmin { get; set; }
    [JsonPropertyName("banned")] public int Banned { get; set; }
    [JsonPropertyName("created_at")] public string? CreatedAt { get; set; }
}

public sealed class Song
{
    [JsonPropertyName("id")] public int Id { get; set; }
    [JsonPropertyName("title")] public string Title { get; set; } = "";
    [JsonPropertyName("artist")] public string Artist { get; set; } = "";
    [JsonPropertyName("genre")] public string Genre { get; set; } = "其他";
    [JsonPropertyName("cover_key")] public string? CoverKey { get; set; }
    [JsonPropertyName("audio_key")] public string AudioKey { get; set; } = "";
    [JsonPropertyName("lyrics_key")] public string? LyricsKey { get; set; }
    [JsonPropertyName("duration")] public int Duration { get; set; }
    [JsonPropertyName("uploader_id")] public int UploaderId { get; set; }
    [JsonPropertyName("uploader_name")] public string? UploaderName { get; set; }
    [JsonPropertyName("uploader_title")] public string? UploaderTitle { get; set; }
    [JsonPropertyName("created_at")] public string CreatedAt { get; set; } = "";

    /// <summary>展示用歌手名（无 artist 时回退到上传者，与 Android 端一致）</summary>
    [JsonIgnore]
    public string DisplayArtist => string.IsNullOrWhiteSpace(Artist)
        ? (string.IsNullOrWhiteSpace(UploaderName) ? "未知" : UploaderName!)
        : Artist;
}

public sealed class SongListResponse
{
    [JsonPropertyName("songs")] public List<Song> Songs { get; set; } = new();
    [JsonPropertyName("total")] public int Total { get; set; }
    [JsonPropertyName("page")] public int Page { get; set; } = 1;
    [JsonPropertyName("limit")] public int Limit { get; set; } = 20;
}

public sealed class SongDetailResponse
{
    [JsonPropertyName("song")] public Song? Song { get; set; }
    [JsonPropertyName("favoriteCount")] public int FavoriteCount { get; set; }
    [JsonPropertyName("favorited")] public bool Favorited { get; set; }
}

public sealed class GenreCount
{
    [JsonPropertyName("genre")] public string Genre { get; set; } = "";
    [JsonPropertyName("count")] public int Count { get; set; }
}

public sealed class GenreListResponse
{
    [JsonPropertyName("genres")] public List<GenreCount> Genres { get; set; } = new();
}

public sealed class AuthResponse
{
    [JsonPropertyName("token")] public string Token { get; set; } = "";
    [JsonPropertyName("user")] public User? User { get; set; }
}

public sealed class MeResponse
{
    [JsonPropertyName("user")] public User? User { get; set; }
}

public sealed class OkResponse
{
    [JsonPropertyName("success")] public bool Success { get; set; } = true;
    [JsonPropertyName("message")] public string? Message { get; set; }
}

public sealed class FavoriteResponse
{
    [JsonPropertyName("success")] public bool Success { get; set; } = true;
    [JsonPropertyName("favorite")] public bool Favorite { get; set; }
    [JsonPropertyName("favoriteCount")] public int FavoriteCount { get; set; }
}

public sealed class ErrorBody
{
    [JsonPropertyName("error")] public string? Error { get; set; }
}

public sealed class Playlist
{
    [JsonPropertyName("id")] public int Id { get; set; }
    [JsonPropertyName("name")] public string Name { get; set; } = "";
    [JsonPropertyName("user_id")] public int UserId { get; set; }
    [JsonPropertyName("owner_name")] public string? OwnerName { get; set; }
    [JsonPropertyName("song_count")] public int SongCount { get; set; }
    [JsonPropertyName("created_at")] public string CreatedAt { get; set; } = "";
}

public sealed class PlaylistListResponse
{
    [JsonPropertyName("playlists")] public List<Playlist> Playlists { get; set; } = new();
}

public sealed class PlaylistDetailResponse
{
    [JsonPropertyName("playlist")] public Playlist? Playlist { get; set; }
    [JsonPropertyName("songs")] public List<Song> Songs { get; set; } = new();
}

public sealed class CreateIdResponse
{
    [JsonPropertyName("success")] public bool Success { get; set; } = true;
    [JsonPropertyName("id")] public int Id { get; set; }
}

// ---------- 请求体 ----------

public sealed class LoginRequest
{
    [JsonPropertyName("email")] public string Email { get; set; } = "";
    [JsonPropertyName("password")] public string Password { get; set; } = "";
}

public sealed class CreatePlaylistRequest
{
    [JsonPropertyName("name")] public string Name { get; set; } = "";
}
