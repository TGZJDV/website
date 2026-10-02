package com.famousmusic.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 底部导航栏外观 */
enum class NavStyle(val label: String, val desc: String) {
    /** 原有样式：Material3 NavigationBar */
    CLASSIC("经典", "Material3 标准底栏，纯色背景 + 药丸指示器"),

    /** 液态玻璃：参照蓝河工具箱 6.15 的样式 */
    LIQUID_GLASS("液态玻璃", "半透明玻璃质感，模糊背景 + 高光边缘 + 液态指示器"),
    ;

    companion object {
        fun fromKey(key: String?): NavStyle =
            entries.firstOrNull { it.name == key } ?: CLASSIC
    }
}

/**
 * 底部导航栏样式设置。
 *
 * 用 SharedPreferences 持久化（与 EqualizerManager / PlaybackStateStore 的做法保持一致），
 * 并通过 StateFlow 暴露给 Compose，切换后立即重组生效。
 */
object NavStyleStore {

    private const val PREFS = "appearance"
    private const val KEY_NAV_STYLE = "nav_style"

    private var prefs: SharedPreferences? = null

    private val _style = MutableStateFlow(NavStyle.CLASSIC)
    val style: StateFlow<NavStyle> = _style.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _style.value = NavStyle.fromKey(p.getString(KEY_NAV_STYLE, null))
    }

    fun set(style: NavStyle) {
        prefs?.edit()?.putString(KEY_NAV_STYLE, style.name)?.apply()
        _style.value = style
    }
}
