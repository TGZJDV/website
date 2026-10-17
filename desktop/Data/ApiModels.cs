using System.Text.Json.Serialization;

namespace FamousMusic.Desktop.Data;

// ============================================================
// 补充的请求 / 响应 DTO。
// 字段名严格照抄 Android 端 Models.kt（后端 api.famousmusic.asia 的 JSON 是
// 混合命名：数据库列用 snake_case，presign/complete 用 camelCase）。
// 单独放一个文件，避免和既有 Models.cs 互相干扰。
// ============================================================

// ---------- 认证 ----------

public sealed class SendCodeRequest
{
    [JsonPropertyName("email")] public string Email { get; set; } = "";
    /// <summary>register 或 reset（后端 zod enum 只允许这两个值）</summary>
    [JsonPropertyName("purpose")] public string Purpose { get; set; } = "";
}

public sealed class RegisterRequest
{
    [JsonPropertyName("email")] public string Email { get; set; } = "";
    [JsonPropertyName("username")] public string Username { get; set; } = "";
    [JsonPropertyName("password")] public string Password { get; set; } = "";
    [JsonPropertyName("code")] public string Code { get; set; } = "";
}

public sealed class ResetPasswordRequest
{
    [JsonPropertyName("email")] public string Email { get; set; } = "";
    [JsonPropertyName("code")] public string Code { get; set; } = "";
    [JsonPropertyName("newPassword")] public string NewPassword { get; set; } = "";
}

// ---------- 上传（presign / complete） ----------

public sealed class PresignRequest
{
    [JsonPropertyName("title")] public string Title { get; set; } = "";
    [JsonPropertyName("artist")] public string Artist { get; set; } = "";
    [JsonPropertyName("genre")] public string Genre { get; set; } = "其他";
    [JsonPropertyName("duration")] public int Duration { get; set; }
    /// <summary>原始音频文件名，后端只取扩展名</summary>
    [JsonPropertyName("audioName")] public string AudioName { get; set; } = "";
    [JsonPropertyName("coverName")] public string? CoverName { get; set; }
    [JsonPropertyName("lyricsName")] public string? LyricsName { get; set; }
}

public sealed class PresignResponse
{
    [JsonPropertyName("success")] public bool Success { get; set; } = true;
    /// <summary>本次上传的 UUID（字符串，不是自增 id）</summary>
    [JsonPropertyName("id")] public string Id { get; set; } = "";
    [JsonPropertyName("audioKey")] public string AudioKey { get; set; } = "";
    [JsonPropertyName("audioUrl")] public string AudioUrl { get; set; } = "";
    [JsonPropertyName("coverKey")] public string? CoverKey { get; set; }
    [JsonPropertyName("coverUrl")] public string? CoverUrl { get; set; }
    [JsonPropertyName("lyricsKey")] public string? LyricsKey { get; set; }
    [JsonPropertyName("lyricsUrl")] public string? LyricsUrl { get; set; }
}

public sealed class CompleteRequest
{
    [JsonPropertyName("title")] public string Title { get; set; } = "";
    [JsonPropertyName("artist")] public string Artist { get; set; } = "";
    [JsonPropertyName("genre")] public string Genre { get; set; } = "其他";
    [JsonPropertyName("duration")] public int Duration { get; set; }
    [JsonPropertyName("audioKey")] public string AudioKey { get; set; } = "";
    [JsonPropertyName("coverKey")] public string? CoverKey { get; set; }
    [JsonPropertyName("lyricsKey")] public string? LyricsKey { get; set; }
}

public sealed class CompleteResponse
{
    [JsonPropertyName("success")] public bool Success { get; set; } = true;
    [JsonPropertyName("id")] public int Id { get; set; }
}

// ---------- 评论 ----------

public sealed class Comment
{
    [JsonPropertyName("id")] public int Id { get; set; }
    [JsonPropertyName("song_id")] public int SongId { get; set; }
    [JsonPropertyName("user_id")] public int UserId { get; set; }
    [JsonPropertyName("username")] public string Username { get; set; } = "";
    [JsonPropertyName("title")] public string? Title { get; set; }
    [JsonPropertyName("avatar_key")] public string? AvatarKey { get; set; }
    [JsonPropertyName("content")] public string Content { get; set; } = "";
    [JsonPropertyName("created_at")] public string CreatedAt { get; set; } = "";
}

public sealed class CommentListResponse
{
    [JsonPropertyName("comments")] public List<Comment> Comments { get; set; } = new();
}

public sealed class CommentCreateResponse
{
    [JsonPropertyName("success")] public bool Success { get; set; } = true;
    [JsonPropertyName("comment")] public Comment? Comment { get; set; }
}

public sealed class CreateCommentRequest
{
    [JsonPropertyName("songId")] public int SongId { get; set; }
    [JsonPropertyName("content")] public string Content { get; set; } = "";
}

// ---------- 歌单歌曲 ----------

public sealed class AddSongRequest
{
    [JsonPropertyName("songId")] public int SongId { get; set; }
}
