package com.famousmusic.app.data

import kotlinx.serialization.json.Json

/** 全局 JSON 配置（后端字段为蛇形命名，模型上用 @SerialName 映射） */
object AppJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
        isLenient = true
    }
}
