package com.tgzjdv.music.session

import com.tgzjdv.music.data.ApiClient
import com.tgzjdv.music.data.ApiException
import com.tgzjdv.music.data.TokenStore
import com.tgzjdv.music.data.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 全局会话状态（登录用户），供 Compose 订阅 */
object AppSession {
    private val _user = MutableStateFlow(TokenStore.user)
    val user: StateFlow<User?> = _user.asStateFlow()

    val current: User? get() = _user.value
    val isLoggedIn: Boolean get() = _user.value != null
    val isAdmin: Boolean get() = _user.value?.isAdmin == 1

    /** 启动时用已存 token 拉取最新用户信息 */
    suspend fun refresh() {
        if (!TokenStore.isLoggedIn) {
            _user.value = null
            return
        }
        runCatching { ApiClient.me() }
            .onSuccess {
                TokenStore.user = it.user
                _user.value = it.user
            }
            .onFailure { e ->
                if ((e as? ApiException)?.status == 401) logout()
            }
    }

    suspend fun login(email: String, password: String): User {
        val res = ApiClient.login(email.trim(), password)
        TokenStore.save(res.token, res.user)
        _user.value = res.user
        return res.user
    }

    suspend fun register(email: String, username: String, password: String, code: String): User {
        val res = ApiClient.register(email.trim(), username.trim(), password, code.trim())
        TokenStore.save(res.token, res.user)
        _user.value = res.user
        return res.user
    }

    fun updateUser(user: User) {
        TokenStore.user = user
        _user.value = user
    }

    fun logout() {
        TokenStore.clear()
        _user.value = null
    }
}
