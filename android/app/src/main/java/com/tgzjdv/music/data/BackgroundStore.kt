package com.tgzjdv.music.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** 自定义背景的来源 */
enum class BgMode {
    /** 不使用自定义背景（默认，走原来的纯色 + 微光） */
    NONE,

    /** 用户从相册选的图（持久化的 content:// URI） */
    LOCAL,

    /** 通过 UAPI 随机图片接口取的图（二进制缓存在应用私有目录） */
    UAPI,
}

/**
 * 应用背景设置。
 *
 * - [BgMode.LOCAL]：保存用户选的图片 URI（需调用方先 takePersistableUriPermission）
 * - [BgMode.UAPI]：把接口返回的图片字节缓存到 `filesDir/bg_uapi.jpg`，
 *   并记住选择的 category / type，方便「换一张」时用同样条件再取
 *
 * 背景图由 `AppRoot` 画在**背景采样层内部**，这样液态玻璃面板能折射它 ——
 * 有了自定义背景，玻璃才有「有内容可折射」的观感。
 */
object BackgroundStore {

    data class State(
        val mode: BgMode = BgMode.NONE,
        val localUri: String? = null,
        val category: String? = null,
        val type: String? = null,
        /** 每次换图自增，用于让 Compose 重新加载图片缓存 */
        val version: Int = 0,
    ) {
        /** UAPI 图片的缓存文件 */
        val uapiFile: File? get() = null // 由 store 提供，避免在 data class 里持有 Context
    }

    private const val PREF = "bg_prefs"
    private const val K_MODE = "mode"
    private const val K_URI = "local_uri"
    private const val K_CATEGORY = "uapi_category"
    private const val K_TYPE = "uapi_type"
    private const val K_VERSION = "version"

    private const val FILE_NAME = "bg_uapi.jpg"

    private var sp: SharedPreferences? = null
    private var appContext: Context? = null

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun init(context: Context) {
        if (sp != null) return
        appContext = context.applicationContext
        val p = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp = p
        _state.value = State(
            mode = runCatching { BgMode.valueOf(p.getString(K_MODE, BgMode.NONE.name).orEmpty()) }
                .getOrDefault(BgMode.NONE),
            localUri = p.getString(K_URI, null),
            category = p.getString(K_CATEGORY, null),
            type = p.getString(K_TYPE, null),
            version = p.getInt(K_VERSION, 0),
        )
    }

    /**
     * UAPI 图片的缓存文件。
     * 文件名带 [State.version]，换图后路径变化 —— 避免 Coil 命中旧图的内存/磁盘缓存。
     */
    fun uapiFile(): File? {
        val ctx = appContext ?: return null
        return File(ctx.filesDir, "bg_uapi_${_state.value.version}.jpg")
    }

    fun refresh() {
        val p = sp ?: return
        _state.value = _state.value.copy(version = p.getInt(K_VERSION, 0))
    }

    fun setNone() {
        sp?.edit()?.putString(K_MODE, BgMode.NONE.name)?.apply()
        _state.value = _state.value.copy(mode = BgMode.NONE, version = _state.value.version + 1)
    }

    fun setLocal(uri: String) {
        sp?.edit()
            ?.putString(K_MODE, BgMode.LOCAL.name)
            ?.putString(K_URI, uri)
            ?.apply()
        _state.value = _state.value.copy(
            mode = BgMode.LOCAL,
            localUri = uri,
            version = _state.value.version + 1,
        )
    }

    /** 保存 UAPI 取回的图片，并记住使用的筛选条件 */
    fun saveUapi(bytes: ByteArray, category: String?, type: String?) {
        val ctx = appContext ?: return
        val next = _state.value.version + 1
        val target = File(ctx.filesDir, "bg_uapi_$next.jpg")
        runCatching { target.writeBytes(bytes) }
        // 清掉旧版本文件，避免堆积
        runCatching {
            ctx.filesDir.listFiles { f -> f.name.startsWith("bg_uapi_") && f.name != target.name }
                ?.forEach { it.delete() }
        }
        sp?.edit()
            ?.putString(K_MODE, BgMode.UAPI.name)
            ?.putString(K_CATEGORY, category)
            ?.putString(K_TYPE, type)
            ?.putInt(K_VERSION, next)
            ?.apply()
        _state.value = _state.value.copy(
            mode = BgMode.UAPI,
            category = category,
            type = type,
            version = next,
        )
    }
}
