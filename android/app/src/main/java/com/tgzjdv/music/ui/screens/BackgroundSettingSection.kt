package com.tgzjdv.music.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tgzjdv.music.data.ApiException
import com.tgzjdv.music.data.BackgroundStore
import com.tgzjdv.music.data.BgMode
import com.tgzjdv.music.data.UapiClient
import com.tgzjdv.music.data.UapiKeyStore
import com.tgzjdv.music.ui.components.glassPanel
import com.tgzjdv.music.ui.theme.AppMuted
import com.tgzjdv.music.ui.theme.AppPrimary
import com.tgzjdv.music.ui.theme.AppSurface2
import com.tgzjdv.music.ui.theme.AppSurface3
import com.tgzjdv.music.ui.theme.AppText
import kotlinx.coroutines.launch

/**
 * 「自定义背景」设置区。
 *
 * 两种来源：
 * 1. **相册图片**：系统照片选择器（PickVisualMedia），URI 持久化保存
 * 2. **UAPI 随机图片**：调用 `https://uapis.cn/api/v1/random/image`
 *    密钥从 [UapiKeyStore]（本页可填）或 `BuildConfig.UAPI_KEY`（android/uapi.properties）读取
 */
@Composable
fun BackgroundSettingSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val bg by BackgroundStore.state.collectAsState()

    var keyInput by remember { mutableStateOf(UapiKeyStore.key()) }
    var category by remember { mutableStateOf(bg.category) }
    var type by remember { mutableStateOf(bg.type) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    // 相册选择器
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            // 尽量拿到持久化读权限，重启后仍可用
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            BackgroundStore.setLocal(uri.toString())
            message = "已应用相册图片"
            isError = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "自定义背景",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = AppText,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        Text(
            "背景会画在液态玻璃的采样层里 —— 有了背景，玻璃面板才有内容可折射。",
            style = MaterialTheme.typography.bodySmall,
            color = AppMuted,
        )
        Spacer(Modifier.height(12.dp))

        // ---------- 关闭 ----------
        BgOptionRow(
            title = "不使用",
            desc = "回到默认的深色背景",
            selected = bg.mode == BgMode.NONE,
            onClick = {
                BackgroundStore.setNone()
                message = null
            },
        )
        Spacer(Modifier.height(10.dp))

        // ---------- 相册 ----------
        BgOptionRow(
            title = "从相册选择",
            desc = if (bg.mode == BgMode.LOCAL) "已应用相册图片" else "用自己的图片当背景",
            selected = bg.mode == BgMode.LOCAL,
            leadingIcon = Icons.Rounded.PhotoLibrary,
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        )
        Spacer(Modifier.height(10.dp))

        // ---------- UAPI 随机图 ----------
        BgOptionRow(
            title = "UAPI 随机图片",
            desc = "从 uapis.cn 随机图库取一张当背景",
            selected = bg.mode == BgMode.UAPI,
            leadingIcon = Icons.Rounded.Image,
            onClick = { /* 具体操作在下方按钮里 */ },
        )

        Spacer(Modifier.height(10.dp))

        // UAPI 面板：密钥 + 类别 + 换一张
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            Text(
                "UAPI 密钥（可选）",
                style = MaterialTheme.typography.bodySmall,
                color = AppMuted,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "实测不填也能用（免费通道）。若填了**错误**的密钥反而会 401，所以不确定就别填。",
                style = MaterialTheme.typography.labelSmall,
                color = AppMuted,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                placeholder = { Text("uapi-xxxxxxxx", color = AppMuted) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AppSurface2,
                    unfocusedContainerColor = AppSurface2,
                    focusedBorderColor = AppSurface3,
                    unfocusedBorderColor = AppSurface3,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton("保存密钥") {
                    UapiKeyStore.setKey(keyInput)
                    message = if (keyInput.isBlank()) "已清空密钥（走免费通道）" else "密钥已保存"
                    isError = false
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        !UapiClient.hasKey() -> "未配置 · 走免费通道"
                        UapiClient.effectiveKey().startsWith("uapi-") -> "已配置"
                        else -> "格式可疑（应以 uapi- 开头）"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (UapiClient.hasKey()) AppPrimary else AppMuted,
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("类别 category（可留空 = 全局随机）", style = MaterialTheme.typography.bodySmall, color = AppMuted)
            Spacer(Modifier.height(8.dp))
            ChipFlow(
                values = listOf(null) + UapiClient.CATEGORIES,
                selected = category,
                labelOf = { v -> v?.let { UapiClient.CATEGORY_LABELS[it] ?: it } ?: "全局随机" },
                onSelect = {
                    category = it
                    // 类别换了以后，原 type 可能不再被支持
                    if (type != null && (it == null || it !in UapiClient.TYPE_CATEGORIES)) type = null
                },
            )

            // type 只在 acg / bq / furry 下有效（文档明确）
            if (category != null && category in UapiClient.TYPE_CATEGORIES) {
                Spacer(Modifier.height(12.dp))
                Text("子类别 type（可留空）", style = MaterialTheme.typography.bodySmall, color = AppMuted)
                Spacer(Modifier.height(8.dp))
                ChipFlow(
                    values = listOf(null) + UapiClient.TYPES,
                    selected = type,
                    labelOf = { v -> v ?: "不筛" },
                    onSelect = { type = it },
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AppPrimary)
                        .clickable(enabled = !loading) {
                            loading = true
                            message = null
                            scope.launch {
                                runCatching { UapiClient.randomImage(category, type) }
                                    .onSuccess {
                                        BackgroundStore.saveUapi(it, category, type)
                                        message = "已获取并应用随机背景"
                                        isError = false
                                    }
                                    .onFailure { e ->
                                        message = (e as? ApiException)?.message ?: "获取失败：${e.message}"
                                        isError = true
                                    }
                                loading = false
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (loading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (bg.mode == BgMode.UAPI) "换一张" else "获取随机背景",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error else AppPrimary,
                )
            }
        }
    }
}

@Composable
private fun TextButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, AppSurface3, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = AppText)
    }
}

@Composable
private fun BgOptionRow(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = if (selected) AppPrimary else AppMuted)
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) AppPrimary else AppText,
            )
            Text(desc, style = MaterialTheme.typography.bodySmall, color = AppMuted)
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AppPrimary),
            )
        }
    }
}

/** 简单的自动换行 chip 组（避免引入额外依赖） */
@Composable
private fun <T> ChipFlow(
    values: List<T>,
    selected: T?,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { v ->
                    val active = v == selected
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (active) AppPrimary.copy(alpha = 0.22f) else AppSurface2)
                            .border(
                                1.dp,
                                if (active) AppPrimary.copy(alpha = 0.6f) else AppSurface3,
                                RoundedCornerShape(percent = 50),
                            )
                            .clickable { onSelect(v) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            labelOf(v),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) AppPrimary else AppMuted,
                        )
                    }
                }
            }
        }
    }
}
