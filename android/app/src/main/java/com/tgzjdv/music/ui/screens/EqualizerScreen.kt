package com.tgzjdv.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tgzjdv.music.playback.EqualizerManager
import com.tgzjdv.music.ui.theme.AppMuted
import com.tgzjdv.music.ui.theme.LocalBottomBarInset
import com.tgzjdv.music.ui.theme.AppPrimary
import com.tgzjdv.music.ui.theme.AppSurface2
import com.tgzjdv.music.ui.theme.AppSurface3
import com.tgzjdv.music.ui.theme.AppText
import kotlin.math.roundToInt

/** 均衡器：预置音效 + 手动调音 + 低音增强 */
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val state by EqualizerManager.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = AppText)
            }
            Text(
                "均衡器",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppText,
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { EqualizerManager.selectPreset(0) },
                enabled = state.available,
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = "重置", tint = AppMuted)
            }
        }

        if (!state.available) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = AppMuted,
                    modifier = Modifier.size(52.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text("音效暂不可用", fontWeight = FontWeight.SemiBold, color = AppText)
                Spacer(Modifier.height(8.dp))
                Text(
                    "均衡器绑定在播放器上。先播放一首歌，再回到这里就能调节。",
                    color = AppMuted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp + LocalBottomBarInset.current),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 总开关
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("启用均衡器", fontWeight = FontWeight.SemiBold, color = AppText)
                        Text(
                            "当前：${state.presetName}",
                            color = AppMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = { EqualizerManager.setEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppPrimary,
                            checkedTrackColor = AppPrimary.copy(alpha = 0.4f),
                        ),
                    )
                }
            }

            // 预置音效
            item {
                Text(
                    "预置音效",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppText,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(EqualizerManager.PRESETS) { i, preset ->
                        FilterChip(
                            selected = state.presetId == i,
                            onClick = { EqualizerManager.selectPreset(i) },
                            label = { Text(preset.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppPrimary.copy(alpha = 0.22f),
                                selectedLabelColor = AppPrimary,
                                containerColor = AppSurface2,
                                labelColor = AppMuted,
                            ),
                        )
                    }
                }
                if (state.presetId < 0) {
                    Text(
                        "已手动调整 → 自定义",
                        color = AppMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            // 频段
            item {
                Text(
                    "频段增益",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppText,
                    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                )
            }
            itemsIndexed(state.bands) { i, band ->
                val level = state.levels.getOrElse(i) { 0 }
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatFreq(band.freqHz),
                            color = AppText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(60.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            formatDb(level),
                            color = if (level == 0.toShort()) AppMuted else AppPrimary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Slider(
                        value = level.toFloat(),
                        onValueChange = { EqualizerManager.setBandLevel(i, it.roundToInt().toShort()) },
                        valueRange = band.minLevel.toFloat()..band.maxLevel.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = AppPrimary,
                            activeTrackColor = AppPrimary,
                            inactiveTrackColor = AppSurface3,
                        ),
                    )
                }
            }

            // 低音增强
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("低音增强", fontWeight = FontWeight.SemiBold, color = AppText)
                        Spacer(Modifier.weight(1f))
                        Text("${state.bassStrength}", color = AppMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = state.bassStrength.toFloat(),
                        onValueChange = { EqualizerManager.setBassStrength(it.roundToInt()) },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(
                            thumbColor = AppPrimary,
                            activeTrackColor = AppPrimary,
                            inactiveTrackColor = AppSurface3,
                        ),
                    )
                    Text(
                        "「超重低音」预置会把这里拉到 900，也可以自己微调",
                        color = AppMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // 响度
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("响度增强", fontWeight = FontWeight.SemiBold, color = AppText)
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (state.loudnessGainMb == 0) "关" else "+${state.loudnessGainMb / 100f} dB",
                            color = AppMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Slider(
                        value = state.loudnessGainMb.toFloat(),
                        onValueChange = { EqualizerManager.setLoudnessGain(it.roundToInt()) },
                        valueRange = 0f..1200f,
                        colors = SliderDefaults.colors(
                            thumbColor = AppPrimary,
                            activeTrackColor = AppPrimary,
                            inactiveTrackColor = AppSurface3,
                        ),
                    )
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(
                        "说明：不同机型支持的频段数量不同（常见 5 段或 10 段），" +
                            "预置音效会按频响曲线自动适配到本机频段。",
                        color = AppMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun formatFreq(hz: Int): String =
    if (hz >= 1000) {
        val k = hz / 1000.0
        if (k == k.toInt().toDouble()) "${k.toInt()}kHz" else String.format("%.1fkHz", k)
    } else {
        "${hz}Hz"
    }

private fun formatDb(mb: Short): String {
    val db = mb / 100f
    return if (mb == 0.toShort()) "0 dB" else String.format("%+.1f dB", db)
}
