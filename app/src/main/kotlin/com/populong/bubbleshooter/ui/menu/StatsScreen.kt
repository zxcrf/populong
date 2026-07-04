package com.populong.bubbleshooter.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.populong.bubbleshooter.AppContainer
import com.populong.bubbleshooter.audio.Sfx
import com.populong.bubbleshooter.ui.theme.Neon
import com.populong.bubbleshooter.ui.theme.NeonPanel

/**
 * The career-stats screen: cumulative [com.populong.bubbleshooter.core.progress.CareerStats]
 * counters plus two values derived from the rest of the save (total stars collected across all
 * levels, best Endless score), grouped into two [NeonPanel]s — 「战斗」for in-run combat counters
 * and 「历程」for overall progression.
 */
@Composable
fun StatsScreen(container: AppContainer, onBack: () -> Unit) {
    val save by container.save.save.collectAsState()
    val stats = save.stats

    val hitRate = remember(stats.shotsFired, stats.shotsPopped) {
        if (stats.shotsFired <= 0L) {
            "—"
        } else {
            "%.1f%%".format(stats.shotsPopped * 100.0 / stats.shotsFired)
        }
    }
    val totalStars = remember(save) { save.levels.values.sumOf { it.stars } }
    val endlessBest = remember(save) { save.endlessHighs.firstOrNull()?.score ?: 0L }

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
                    text = "生涯统计",
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
                        text = "战斗",
                        color = Neon.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    StatRow(label = "总发射数", value = "${stats.shotsFired}")
                    StatRow(label = "命中率", value = hitRate)
                    StatRow(label = "消除泡泡", value = "${stats.bubblesPopped}")
                    StatRow(label = "坠落泡泡", value = "${stats.bubblesDropped}")
                    StatRow(label = "最高连击", value = "${stats.maxCombo}")
                    StatRow(label = "反弹入库", value = "${stats.bankShots}")
                    StatRow(label = "引爆炸弹", value = "${stats.bombsDetonated}")
                    StatRow(label = "触发 Fever", value = "${stats.feversTriggered}")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            NeonPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "历程",
                        color = Neon.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    StatRow(label = "通关数", value = "${stats.levelsCompleted}")
                    StatRow(label = "三星关卡", value = "${stats.threeStarLevels}")
                    StatRow(label = "累计得分", value = "${stats.totalScore}")
                    StatRow(label = "收集星星", value = "$totalStars")
                    StatRow(label = "无尽最高分", value = "$endlessBest")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** One label/value stat line: dim label on the left, bold monospace-ish value on the right. */
@Composable
private fun StatRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = Neon.textDim,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = Neon.cyan,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}
