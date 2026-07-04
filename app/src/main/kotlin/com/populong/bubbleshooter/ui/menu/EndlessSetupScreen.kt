package com.populong.bubbleshooter.ui.menu

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.populong.bubbleshooter.AppContainer
import com.populong.bubbleshooter.audio.Sfx
import com.populong.bubbleshooter.core.mode.Mutator
import com.populong.bubbleshooter.core.mode.totalMultiplier
import com.populong.bubbleshooter.ui.theme.Neon
import com.populong.bubbleshooter.ui.theme.NeonPanel
import java.util.Locale

/** Chinese display name + effect string for each [Mutator]. */
private fun Mutator.displayLabel(): String = when (this) {
    Mutator.FASTER_DESCENT -> "加速下压 ×1.5"
    Mutator.EXTRA_COLOR -> "第五色 ×1.4"
    Mutator.SHORT_AIM -> "短瞄准线 ×1.3"
    Mutator.NARROW_FIELD -> "窄场地 ×1.25"
}

/**
 * Endless-mode setup: pick optional [Mutator]s (each raises risk and score multiplier), see the
 * combined multiplier and the top-5 leaderboard, then start a run.
 */
@Composable
fun EndlessSetupScreen(container: AppContainer, onStart: (Set<Mutator>) -> Unit, onBack: () -> Unit) {
    val save by container.save.save.collectAsState()
    var selected by remember { mutableStateOf<Set<Mutator>>(emptySet()) }
    val multiplier = selected.totalMultiplier()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(Neon.spaceGradient)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "←",
                    color = Neon.textPrimary,
                    fontSize = 22.sp,
                    modifier = Modifier.clickable {
                        container.sfx.play(Sfx.UI_TAP)
                        onBack()
                    },
                )
                Text(
                    text = "无尽模式",
                    color = Neon.cyan,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(22.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            NeonPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "调整变量",
                        color = Neon.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    for (mutator in Mutator.entries) {
                        MutatorToggleRow(
                            mutator = mutator,
                            checked = mutator in selected,
                            onToggle = {
                                container.sfx.play(Sfx.UI_TAP)
                                selected = if (mutator in selected) selected - mutator else selected + mutator
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "总倍率 ×${String.format(Locale.US, "%.2f", multiplier)}",
                        color = Neon.gold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            NeonPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "最高纪录",
                        color = Neon.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val top5 = save.endlessHighs.take(5)
                    if (top5.isEmpty()) {
                        Text(text = "暂无纪录，去创造第一个吧！", color = Neon.textDim, fontSize = 13.sp)
                    } else {
                        top5.forEachIndexed { index, record ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "${index + 1}.",
                                    color = Neon.textDim,
                                    fontSize = 14.sp,
                                    modifier = Modifier.width(28.dp),
                                )
                                Text(
                                    text = "${record.score}",
                                    color = Neon.textPrimary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                if (record.mutators.isNotEmpty()) {
                                    Text(
                                        text = record.mutators.joinToString(" ") { it.displayLabel().substringBefore(" ") },
                                        color = Neon.textDim,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "开始",
                color = Neon.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        container.sfx.play(Sfx.UI_TAP)
                        container.haptics.tick()
                        onStart(selected)
                    }
                    .background(Color(0x330A1030), RoundedCornerShape(14.dp))
                    .padding(vertical = 14.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** A single mutator row with a custom (non-Material) checkbox-style toggle indicator. */
@Composable
private fun MutatorToggleRow(mutator: Mutator, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    color = if (checked) Neon.cyan.copy(alpha = 0.85f) else Color(0x22FFFFFF),
                    shape = RoundedCornerShape(5.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text(text = "✓", color = Neon.bgTop, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = mutator.displayLabel(),
            color = if (checked) Neon.textPrimary else Neon.textDim,
            fontSize = 15.sp,
        )
    }
}
