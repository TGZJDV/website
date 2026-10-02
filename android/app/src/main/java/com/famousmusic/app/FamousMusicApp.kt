package com.famousmusic.app

import android.app.Application
import com.famousmusic.app.data.ApiClient
import com.famousmusic.app.data.TokenStore
import com.famousmusic.app.ui.theme.NavStyleStore

class FamousMusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenStore.init(this)
        // HTTP 响应缓存（磁盘层放在 cacheDir/http，冷启动也能秒出列表）
        ApiClient.initCache(this)
        // 底部导航栏样式（经典 / 液态玻璃）
        NavStyleStore.init(this)
    }
}
