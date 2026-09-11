package com.famousmusic.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.famousmusic.app.data.ApiClient
import com.famousmusic.app.session.AppSession
import com.famousmusic.app.ui.theme.AppMuted
import com.famousmusic.app.ui.theme.AppPrimary
import com.famousmusic.app.ui.theme.AppSurface2
import kotlinx.coroutines.launch

/** 登录页 */
@Composable
fun LoginScreen(
    onDone: () -> Unit,
    onGoRegister: () -> Unit,
    onGoForgot: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("登录云音乐", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        AuthField("邮箱", email, { email = it }, KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        AuthField("密码", password, { password = it }, KeyboardType.Password, isPassword = true)

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    error = "请填写邮箱和密码"
                    return@Button
                }
                scope.launch {
                    loading = true
                    error = null
                    runCatching { AppSession.login(email, password) }
                        .onSuccess { onDone() }
                        .onFailure { error = it.message }
                    loading = false
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
        ) {
            Text(if (loading) "登录中…" else "登录", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onGoRegister) { Text("注册新账号", color = AppPrimary) }
            TextButton(onClick = onGoForgot) { Text("忘记密码？", color = AppMuted) }
        }
    }
}

/** 注册页（邮箱验证码） */
@Composable
fun RegisterScreen(onDone: () -> Unit, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("注册云音乐", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        AuthField("邮箱", email, { email = it }, KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                AuthField("验证码", code, { code = it }, KeyboardType.Number)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (email.isBlank()) {
                        error = "请先填写邮箱"
                        return@Button
                    }
                    scope.launch {
                        sending = true
                        error = null
                        runCatching { ApiClient.sendCode(email, "register") }
                            .onSuccess { info = "验证码已发送，请查收邮箱" }
                            .onFailure { error = it.message }
                        sending = false
                    }
                },
                enabled = !sending,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = AppSurface2),
            ) {
                Text(if (sending) "发送中" else "获取验证码", color = AppPrimary, style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        AuthField("用户名", username, { username = it }, KeyboardType.Text)
        Spacer(Modifier.height(12.dp))
        AuthField("密码（至少 6 位）", password, { password = it }, KeyboardType.Password, isPassword = true)

        info?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = AppPrimary, style = MaterialTheme.typography.bodySmall)
        }
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (email.isBlank() || code.isBlank() || username.isBlank() || password.isBlank()) {
                    error = "请填写全部信息"
                    return@Button
                }
                scope.launch {
                    loading = true
                    error = null
                    runCatching { AppSession.register(email, username, password, code) }
                        .onSuccess { onDone() }
                        .onFailure { error = it.message }
                    loading = false
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
        ) {
            Text(if (loading) "注册中…" else "注册", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("已有账号？去登录", color = AppMuted)
        }
    }
}

/** 忘记密码 */
@Composable
fun ForgotScreen(onDone: () -> Unit, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("重置密码", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        AuthField("注册邮箱", email, { email = it }, KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                AuthField("验证码", code, { code = it }, KeyboardType.Number)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (email.isBlank()) {
                        error = "请先填写邮箱"
                        return@Button
                    }
                    scope.launch {
                        sending = true
                        error = null
                        runCatching { ApiClient.forgot(email) }
                            .onSuccess { info = "重置验证码已发送" }
                            .onFailure { error = it.message }
                        sending = false
                    }
                },
                enabled = !sending,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = AppSurface2),
            ) {
                Text(if (sending) "发送中" else "获取验证码", color = AppPrimary, style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        AuthField("新密码（至少 6 位）", newPassword, { newPassword = it }, KeyboardType.Password, isPassword = true)

        info?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = AppPrimary, style = MaterialTheme.typography.bodySmall)
        }
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (email.isBlank() || code.isBlank() || newPassword.isBlank()) {
                    error = "请填写全部信息"
                    return@Button
                }
                scope.launch {
                    loading = true
                    error = null
                    runCatching { ApiClient.resetPassword(email, code, newPassword) }
                        .onSuccess { onDone() }
                        .onFailure { error = it.message }
                    loading = false
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
        ) {
            Text(if (loading) "提交中…" else "重置密码", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("返回登录", color = AppMuted)
        }
    }
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
