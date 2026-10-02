package com.famousmusic.app.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import kotlin.math.ln
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 均衡器 / 音效管理器。
 *
 * - 绑定到播放器的音频会话（[attach]），会话变化时自动重建
 * - 预置音效用「频响曲线」描述，再按设备实际的频段中心频率做对数插值，
 *   所以 5 段机型和 10 段机型都能得到等价听感
 * - 「超重低音」额外挂 [BassBoost]
 *
 * ⚠️ **效果链一旦建立就始终保持 enabled**：
 * 关闭音效时不 `setEnabled(false)`，而是把所有增益归零。
 * 因为 disable 会让 Android 重新路由音频管线，切换瞬间会出现音量突变/爆音。
 */
object EqualizerManager {

    private const val TAG = "EqualizerManager"

    /** 一个物理频段 */
    data class BandInfo(val index: Int, val freqHz: Int, val minLevel: Short, val maxLevel: Short)

    data class EqState(
        val available: Boolean = false,
        /** 音效是否生效（关闭时表示「已归零」，效果链本身仍在） */
        val enabled: Boolean = false,
        /** 当前预置下标；-1 表示用户手动调过 = 自定义 */
        val presetId: Int = 0,
        val bands: List<BandInfo> = emptyList(),
        /** 当前各频段电平（millibel），与 [bands] 一一对应 */
        val levels: List<Short> = emptyList(),
        /** 低音增强强度 0..1000 */
        val bassStrength: Int = 0,
        val loudnessGainMb: Int = 0,
    ) {
        val presetName: String
            get() = when {
                !enabled -> "关闭"
                presetId < 0 -> "自定义"
                presetId in PRESETS.indices -> PRESETS[presetId].name
                else -> "关闭"
            }
    }

    /**
     * 预置音效。[curve] 是「频率(Hz) → 增益(dB)」的锚点，
     * 实际频段落在这条曲线上做对数插值。
     */
    data class Preset(val name: String, val curve: Map<Int, Float>, val bass: Int)

    private fun curve(vararg pts: Pair<Int, Float>) = pts.toMap()

    val PRESETS: List<Preset> = listOf(
        Preset("关闭", emptyMap(), 0),
        // 低频重推的同时，高频也略微抬一点，避免听起来发闷
        Preset(
            "超重低音",
            curve(
                31 to 13f, 62 to 11f, 125 to 7f, 250 to 3f,
                1000 to 0f, 4000 to 1f, 8000 to 2.5f, 16000 to 3f,
            ),
            900,
        ),
        Preset(
            "低音增强",
            curve(
                31 to 8f, 62 to 6f, 125 to 4f, 250 to 2f,
                1000 to 0f, 4000 to 0.5f, 8000 to 1f, 16000 to 1f,
            ),
            450,
        ),
        Preset(
            "人声",
            curve(31 to -6f, 62 to -3f, 125 to 0f, 250 to 3f, 1000 to 5f, 4000 to 4f, 8000 to 2f, 16000 to 0f),
            0,
        ),
        Preset(
            "流行",
            curve(31 to -2f, 62 to -1f, 125 to 1f, 250 to 3f, 1000 to 4f, 4000 to 3f, 8000 to 1f, 16000 to -1f),
            150,
        ),
        Preset(
            "摇滚",
            curve(31 to 5f, 62 to 4f, 125 to 2f, 250 to -1f, 1000 to -2f, 4000 to 1f, 8000 to 4f, 16000 to 5f),
            300,
        ),
        Preset(
            "爵士",
            curve(31 to 4f, 62 to 3f, 125 to 1f, 250 to 1f, 1000 to 1f, 4000 to 0f, 8000 to 2f, 16000 to 3f),
            200,
        ),
        Preset(
            "古典",
            curve(31 to 4f, 62 to 3f, 125 to 2f, 250 to 1f, 1000 to 0f, 4000 to -1f, 8000 to -3f, 16000 to -4f),
            0,
        ),
        Preset(
            "电子",
            curve(31 to 6f, 62 to 5f, 125 to 2f, 250 to 0f, 1000 to -1f, 4000 to 2f, 8000 to 4f, 16000 to 5f),
            350,
        ),
        Preset(
            "深夜",
            curve(31 to -4f, 62 to -3f, 125 to 0f, 250 to 2f, 1000 to 3f, 4000 to 1f, 8000 to -1f, 16000 to -2f),
            0,
        ),
    )

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudness: LoudnessEnhancer? = null
    private var prefs: SharedPreferences? = null

    @Volatile
    private var appContext: Context? = null

    /** 调试探针：写外部 files 目录，便于 adb 取证（release 版 Log 会被裁掉） */
    private fun log(msg: String) {
        Log.i(TAG, msg)
        val ctx = appContext ?: return
        runCatching {
            java.io.File(ctx.getExternalFilesDir(null), "probe.log")
                .appendText("${System.currentTimeMillis()} EQ $msg\n")
        }
    }

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    /** 绑定到指定音频会话；会话变化时调用会先释放旧的 */
    fun attach(context: Context, audioSessionId: Int) {
        if (audioSessionId == 0) return
        appContext = context.applicationContext
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences("equalizer", Context.MODE_PRIVATE)
        }
        release()
        runCatching {
            val eq = Equalizer(0, audioSessionId)
            val range = eq.bandLevelRange
            val bands = (0 until eq.numberOfBands.toInt()).map { i ->
                BandInfo(
                    index = i,
                    freqHz = eq.getCenterFreq(i.toShort()) / 1000, // 返回单位是 mHz
                    minLevel = range[0],
                    maxLevel = range[1],
                )
            }
            equalizer = eq
            bassBoost = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
            loudness = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()
            Log.i(TAG, "attach session=$audioSessionId bands=${bands.map { it.freqHz }} range=${range.toList()}")

            _state.value = EqState(
                available = true,
                enabled = false,
                presetId = 0,
                bands = bands,
                levels = List(bands.size) { 0 },
                bassStrength = 0,
                loudnessGainMb = 0,
            )
            // 效果链立即建立并保持 enabled，之后只调增益，不再 disable
            runCatching {
                eq.enabled = true
                bands.indices.forEach { eq.setBandLevel(it.toShort(), 0) }
                bassBoost?.let { it.enabled = true; it.setStrength(0) }
                loudness?.let { it.enabled = true; it.setTargetGain(0) }
            }

            // 恢复上次的选择
            val wantEnabled = prefs?.getBoolean("enabled", false) ?: false
            val lastPreset = prefs?.getInt("lastPreset", 1) ?: 1
            val savedLoudness = prefs?.getInt("loudness", 0) ?: 0
            log("attach session=$audioSessionId bands=${bands.map { it.freqHz }} saved(enabled=$wantEnabled preset=$lastPreset loudness=$savedLoudness)")
            if (wantEnabled) selectPreset(lastPreset) else applyFlat()
            if (savedLoudness > 0) setLoudnessGain(savedLoudness)
            log("restored -> enabled=${_state.value.enabled} presetId=${_state.value.presetId} name=${_state.value.presetName} levels=${_state.value.levels}")
        }.onFailure {
            Log.w(TAG, "均衡器不可用", it)
            _state.value = EqState(available = false)
        }
    }

    /** 只有销毁时才真正释放效果链；释放前先归零，减小管线重路由的冲击 */
    fun release() {
        runCatching {
            equalizer?.let { eq ->
                _state.value.bands.indices.forEach { runCatching { eq.setBandLevel(it.toShort(), 0) } }
                eq.enabled = false
                eq.release()
            }
            bassBoost?.let { it.enabled = false; it.release() }
            loudness?.let { it.enabled = false; it.release() }
        }
        equalizer = null
        bassBoost = null
        loudness = null
        _state.update { it.copy(available = false, enabled = false) }
    }

    /**
     * 总开关。
     * ⚠️ 关闭时**不 disable 效果链**，只把增益归零 —— 否则切换瞬间音量会突变。
     */
    fun setEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean("enabled", enabled)?.apply()
        log("setEnabled $enabled")
        if (enabled) {
            val last = prefs?.getInt("lastPreset", 1) ?: 1
            selectPreset(if (last > 0) last else 1)
        } else {
            applyFlat()
        }
    }

    /** 应用预置音效 */
    fun selectPreset(index: Int) {
        val eq = equalizer ?: return
        val preset = PRESETS.getOrNull(index) ?: return
        prefs?.edit()?.putInt("preset", index)?.apply()
        if (preset.curve.isEmpty()) {
            // 选「关闭」＝用户主动关闭，要把开关状态一起存下来
            prefs?.edit()?.putBoolean("enabled", false)?.apply()
            applyFlat()
            log("selectPreset 关闭 -> enabled=false")
            return
        }
        // ⚠️ 这里必须同时持久化 enabled：否则重启后只恢复了预置下标，
        // 开关仍是默认的 false，看起来就像「均衡器被重置了」
        prefs?.edit()?.putInt("lastPreset", index)?.putBoolean("enabled", true)?.apply()
        val s = _state.value
        val levels = s.bands.map { b -> levelFor(preset.curve, b.freqHz, b.minLevel, b.maxLevel) }
        runCatching {
            eq.enabled = true
            levels.forEachIndexed { i, lv -> eq.setBandLevel(i.toShort(), lv) }
            bassBoost?.let {
                it.enabled = true
                it.setStrength(preset.bass.toShort())
            }
        }.onFailure { Log.w(TAG, "应用预置失败", it) }
        _state.update {
            it.copy(enabled = true, presetId = index, levels = levels, bassStrength = preset.bass)
        }
        log("selectPreset ${preset.name} enabled=true levels=$levels bass=${preset.bass}")
    }

    /** 手动调整某个频段 → 进入「自定义」 */
    fun setBandLevel(bandIndex: Int, level: Short) {
        val eq = equalizer ?: return
        runCatching {
            eq.enabled = true
            eq.setBandLevel(bandIndex.toShort(), level)
        }
        _state.update { s ->
            val levels = s.levels.toMutableList()
            if (bandIndex in levels.indices) levels[bandIndex] = level
            s.copy(enabled = true, presetId = -1, levels = levels)
        }
        prefs?.edit()?.putBoolean("enabled", true)?.apply()
    }

    /** 低音增强强度 0..1000 */
    fun setBassStrength(strength: Int) {
        val v = strength.coerceIn(0, 1000)
        runCatching {
            bassBoost?.enabled = true
            bassBoost?.setStrength(v.toShort())
        }
        _state.update { it.copy(bassStrength = v, enabled = true) }
        prefs?.edit()?.putInt("bass", v)?.putBoolean("enabled", true)?.apply()
    }

    /** 响度增强，单位 millibel；0 表示不增强（效果链保持启用，只是增益为 0） */
    fun setLoudnessGain(gainMb: Int) {
        val v = gainMb.coerceIn(0, 1500)
        runCatching {
            loudness?.enabled = true
            loudness?.setTargetGain(v)
        }
        _state.update { it.copy(loudnessGainMb = v) }
        prefs?.edit()?.putInt("loudness", v)?.apply()
    }

    /**
     * 全部归零 = 听感关闭，但效果链保持 enabled。
     * 这是「关闭均衡器不出爆音」的关键：不动 enabled，就没有音频管线重路由。
     */
    private fun applyFlat() {
        val eq = equalizer ?: return
        runCatching {
            eq.enabled = true
            _state.value.bands.indices.forEach { eq.setBandLevel(it.toShort(), 0) }
            bassBoost?.let { it.enabled = true; it.setStrength(0) }
        }
        _state.update {
            it.copy(
                enabled = false,
                presetId = 0,
                levels = List(it.bands.size) { 0 },
                bassStrength = 0,
            )
        }
    }

    /**
     * 把频响曲线映射到某个实际频段：
     * 频率取对数插值，增益由 dB 换算成 millibel 并夹到设备允许范围。
     */
    private fun levelFor(curve: Map<Int, Float>, freqHz: Int, minLevel: Short, maxLevel: Short): Short {
        if (curve.isEmpty()) return 0
        val keys = curve.keys.sorted()
        val gainDb = when {
            freqHz <= keys.first() -> curve.getValue(keys.first())
            freqHz >= keys.last() -> curve.getValue(keys.last())
            else -> {
                val hi = keys.first { it >= freqHz }
                val lo = keys.last { it <= freqHz }
                if (hi == lo) {
                    curve.getValue(hi)
                } else {
                    val t = (ln(freqHz.toDouble()) - ln(lo.toDouble())) /
                        (ln(hi.toDouble()) - ln(lo.toDouble()))
                    (curve.getValue(lo) + (curve.getValue(hi) - curve.getValue(lo)) * t).toFloat()
                }
            }
        }
        val mb = (gainDb * 100).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt())
        return mb.toShort()
    }
}
