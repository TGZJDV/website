package com.tgzjdv.music.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 液态玻璃的观感调节。
 *
 * [translucency] 0f = 最实（压暗层最重），1f = 最通透（几乎只有折射和高光）。
 * 由 `glassPanel` / `glassCircle` 读取，改完立即生效并持久化。
 */
object GlassTuningStore {
    private const val PREF = "glass_tuning"
    private const val K_TRANSLUCENCY = "translucency"

    /** 默认值：和之前写死的 0.20 压暗大致对应 */
    const val DEFAULT_TRANSLUCENCY = 0.45f

    private var sp: SharedPreferences? = null

    private val _translucency = MutableStateFlow(DEFAULT_TRANSLUCENCY)
    val translucency: StateFlow<Float> = _translucency.asStateFlow()

    fun init(context: Context) {
        if (sp != null) return
        val p = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp = p
        _translucency.value = p.getFloat(K_TRANSLUCENCY, DEFAULT_TRANSLUCENCY)
    }

    fun setTranslucency(value: Float) {
        val v = value.coerceIn(0f, 1f)
        _translucency.value = v
        sp?.edit()?.putFloat(K_TRANSLUCENCY, v)?.apply()
    }

    /** 当前透明度对应的压暗层 alpha（越通透 → alpha 越小） */
    fun tintAlpha(base: Float = 0.45f): Float =
        (base * (1f - _translucency.value)).coerceIn(0f, 0.9f)

    /** 玻璃圆片的高光白 alpha（越通透 → 越薄） */
    fun surfaceAlpha(base: Float = 0.18f): Float =
        (base * (1f - _translucency.value * 0.8f)).coerceIn(0f, 0.6f)
}
