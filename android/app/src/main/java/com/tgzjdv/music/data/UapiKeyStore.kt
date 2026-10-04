package com.tgzjdv.music.data

import android.content.Context
import android.content.SharedPreferences

/**
 * UAPI 密钥存储。
 *
 * - 默认空；用户在「设置 → 自定义背景」里填入后存 SharedPreferences。
 * - 若构建期通过 `android/uapi.properties` 的 `UAPI_KEY` 注入了密钥，
 *   [UapiClient.effectiveKey] 会在本地为空时回退到 `BuildConfig.UAPI_KEY`。
 * - 任何情况下都不把密钥写进代码，也不拼进 URL。
 */
object UapiKeyStore {
    private const val PREF = "uapi_prefs"
    private const val KEY = "uapi_key"

    private var sp: SharedPreferences? = null

    fun init(context: Context) {
        if (sp == null) sp = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }

    private fun prefs(): SharedPreferences? = sp

    fun key(): String = prefs()?.getString(KEY, "").orEmpty()

    fun setKey(value: String) {
        prefs()?.edit()?.putString(KEY, value.trim())?.apply()
    }
}
