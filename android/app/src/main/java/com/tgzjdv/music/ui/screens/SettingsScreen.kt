package com.tgzjdv.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tgzjdv.music.ui.theme.AppMuted
import com.tgzjdv.music.ui.theme.AppPrimary
import com.tgzjdv.music.ui.theme.AppSurface2
import com.tgzjdv.music.ui.theme.AppSurface3
import com.tgzjdv.music.ui.theme.AppText
import com.tgzjdv.music.ui.theme.NavStyle
import com.tgzjdv.music.ui.theme.NavStyleStore

/** 设置：目前只有底部导航栏外观 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val current by NavStyleStore.style.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = AppText)
            }
            Text(
                "设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppText,
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "底部导航栏样式",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppText,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            Text(
                "切换后立即生效，重启应用后保持选择。",
                style = MaterialTheme.typography.bodySmall,
                color = AppMuted,
            )
            Spacer(Modifier.height(12.dp))

            NavStyle.entries.forEach { style ->
                NavStyleOption(
                    style = style,
                    selected = current == style,
                    onClick = { NavStyleStore.set(style) },
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "液态玻璃样式的设计取自本机「蓝河工具箱」的底部导航栏。",
                style = MaterialTheme.typography.bodySmall,
                color = AppMuted,
            )

            Spacer(Modifier.height(20.dp))
            BackgroundSettingSection()

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun NavStyleOption(style: NavStyle, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) AppPrimary.copy(alpha = 0.10f) else AppSurface2)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) AppPrimary.copy(alpha = 0.65f) else AppSurface3,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavStylePreview(style)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                style.label,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) AppPrimary else AppText,
            )
            Spacer(Modifier.height(2.dp))
            Text(style.desc, style = MaterialTheme.typography.bodySmall, color = AppMuted)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) AppPrimary else AppMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 一张小示意图，直观看出两种样式的差别 */
@Composable
private fun NavStylePreview(style: NavStyle) {
    val shape = RoundedCornerShape(if (style == NavStyle.LIQUID_GLASS) 14.dp else 6.dp)
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2B2B33), Color(0xFF141419)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp)
                .clip(shape)
                .then(
                    when (style) {
                        NavStyle.CLASSIC ->
                            Modifier.background(Color(0xFF2A2A2A))
                        NavStyle.LIQUID_GLASS ->
                            Modifier
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.22f),
                                            Color.White.copy(alpha = 0.06f),
                                        ),
                                    ),
                                )
                                .border(
                                    0.8.dp,
                                    Color.White.copy(alpha = 0.35f),
                                    shape,
                                )
                    },
                )
                .padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .size(if (i == 0) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i == 0) AppPrimary else AppMuted.copy(alpha = 0.55f)),
                )
            }
        }
    }
}
