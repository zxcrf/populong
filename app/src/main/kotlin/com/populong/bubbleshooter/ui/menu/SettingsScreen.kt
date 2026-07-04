package com.populong.bubbleshooter.ui.menu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.populong.bubbleshooter.AppContainer
import com.populong.bubbleshooter.audio.MusicStyle
import com.populong.bubbleshooter.audio.Sfx
import com.populong.bubbleshooter.ui.theme.Neon
import com.populong.bubbleshooter.ui.theme.NeonPanel

private const val TRACK_WIDTH_DP = 44
private const val TRACK_HEIGHT_DP = 24
private const val KNOB_SIZE_DP = 18
private const val KNOB_INSET_DP = 3
private const val KNOB_TRAVEL_DP = TRACK_WIDTH_DP - KNOB_SIZE_DP - KNOB_INSET_DP * 2

/**
 * The settings screen: audio/haptics toggles backed by [AppContainer.setSound] /
 * [AppContainer.setHaptics] (which both update the runtime toggle and persist the choice into
 * [AppContainer.save]), plus a small「关于」panel with the game's name, tagline, and version.
 *
 * The toggles are drawn with a bespoke rounded track + animated knob rather than the Material
 * `Switch`, matching the rest of the deep-space chrome.
 */
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(Neon.spaceGradient)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            container.sfx.play(Sfx.UI_TAP)
                            onBack()
                        },
                ) {
                    Text(text = "←", color = Neon.textPrimary, fontSize = 24.sp)
                }
                Text(
                    text = "设置",
                    color = Neon.cyan,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            NeonPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingToggleRow(
                        label = "音效",
                        checked = container.soundEnabled.value,
                        onToggle = { next ->
                            // The tap itself only plays if sound was (or still is) on; toggling
                            // off first would silently swallow this confirmation tick.
                            if (container.soundEnabled.value) container.sfx.play(Sfx.UI_TAP)
                            container.setSound(next)
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingToggleRow(
                        label = "震动反馈",
                        checked = container.hapticsEnabled.value,
                        onToggle = { next ->
                            container.sfx.play(Sfx.UI_TAP)
                            container.setHaptics(next)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            NeonPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingToggleRow(
                        label = "背景音乐",
                        checked = container.musicEnabled.value,
                        onToggle = { next ->
                            container.sfx.play(Sfx.UI_TAP)
                            container.setMusic(next)
                        },
                    )
                    if (container.musicEnabled.value) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            MusicStyleChip(container, MusicStyle.CHIPTUNE, "街机")
                            MusicStyleChip(container, MusicStyle.SYNTHWAVE, "太空")
                            MusicStyleChip(container, MusicStyle.KAWAII, "轻快")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            NeonPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "关于",
                        color = Neon.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "泡泡星河",
                        color = Neon.cyan,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "本地离线 · 程序合成音效 · 2000 关",
                        color = Neon.textDim,
                        fontSize = 13.sp,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "版本 1.0.0",
                        color = Neon.textDim,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** A single labeled row pairing a settings name with a [NeonToggleSwitch]. */
@Composable
private fun SettingToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = Neon.textPrimary,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        NeonToggleSwitch(checked = checked, onCheckedChange = onToggle)
    }
}

/**
 * A rounded pill track with an animated circular knob, standing in for the Material `Switch`
 * so the control matches the deep-space neon chrome (cyan when on, dim slate when off).
 */
@Composable
private fun NeonToggleSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val knobOffset by animateFloatAsState(
        targetValue = if (checked) KNOB_TRAVEL_DP.toFloat() else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "toggle-knob-offset",
    )
    val trackColor = if (checked) Neon.cyan.copy(alpha = 0.85f) else Color(0x33FFFFFF)

    Box(
        modifier = Modifier
            .size(width = TRACK_WIDTH_DP.dp, height = TRACK_HEIGHT_DP.dp)
            .clickable { onCheckedChange(!checked) }
            .background(color = trackColor, shape = RoundedCornerShape(percent = 50)),
    ) {
        Box(
            modifier = Modifier
                .padding(KNOB_INSET_DP.dp)
                .offset(x = knobOffset.dp)
                .size(KNOB_SIZE_DP.dp)
                .background(color = Neon.textPrimary, shape = CircleShape),
        )
    }
}

/**
 * A selectable music-style chip: bright cyan border and fill when it is the active
 * [MusicStyle], dim slate otherwise. Tapping switches the looping BGM immediately.
 */
@Composable
private fun MusicStyleChip(container: AppContainer, style: MusicStyle, tagline: String) {
    val selected = container.musicStyle.value == style
    val border = if (selected) Neon.cyan else Color(0x33FFFFFF)
    val fill = if (selected) Color(0x334DE3FF) else Color(0x1AFFFFFF)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable {
                container.sfx.play(Sfx.UI_TAP)
                container.setMusicStyle(style)
            }
            .background(color = fill, shape = RoundedCornerShape(10.dp))
            .border(width = 1.dp, color = border, shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = style.title,
            color = if (selected) Neon.cyan else Neon.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = tagline,
            color = Neon.textDim,
            fontSize = 11.sp,
        )
    }
}
