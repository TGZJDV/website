package com.famousmusic.app

import android.app.Application
import com.famousmusic.app.data.TokenStore

class FamousMusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenStore.init(this)
    }
}
