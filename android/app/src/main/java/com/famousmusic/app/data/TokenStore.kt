package com.famousmusic.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 会话存储：token 与当前用户（SharedPreferences 同步读写，便于 API 客户端直接使用）
 */
object TokenStore {
    private const val PREF = "famousmusic_session"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER = "user_json"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        }
    }

    var token: String?
        get() = prefs?.getString(KEY_TOKEN, null)
        set(value) {
            prefs?.edit()?.apply {
                if (value.isNullOrBlank()) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
            }?.apply()
        }

    var user: User?
        get() = prefs?.getString(KEY_USER, null)?.let { raw ->
            runCatching { AppJson.instance.decodeFromString(User.serializer(), raw) }.getOrNull()
        }
        set(value) {
            prefs?.edit()?.apply {
                if (value == null) remove(KEY_USER)
                else putString(KEY_USER, AppJson.instance.encodeToString(User.serializer(), value))
            }?.apply()
        }

    fun save(token: String, user: User) {
        this.token = token
        this.user = user
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }

    val isLoggedIn: Boolean get() = !token.isNullOrBlank()
}
