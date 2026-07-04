package com.populong.bubbleshooter.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.populong.bubbleshooter.core.level.DailyLevel
import com.populong.bubbleshooter.core.mode.GameMode
import java.time.LocalDate
import java.time.YearMonth
import com.populong.bubbleshooter.ui.theme.Neon
import com.populong.bubbleshooter.ui.theme.NeonPanel

/**
 * Daily-challenge setup: streak, this month's completion calendar, and today's start/complete
 * status. The day boundary is UTC (`epochMillis / 86_400_000`), matching [DailyLevel.forEpochDay]
 * and [com.populong.bubbleshooter.core.progress.SaveData.daily] exactly, rather than the device's
 * local calendar day — so the month grid is built from that same epoch day rather than
 * [LocalDate.now] to avoid an off-by-one near midnight.
 */
@Composable
fun DailyScreen(container: AppContainer, onStart: (GameMode) -> Unit, onBack: () -> Unit) {
    val save by container.save.save.collectAsState()
    val todayEpochDay = remember { System.currentTimeMillis() / 86_400_000L }
    val today = remember(todayEpochDay) { LocalDate.ofEpochDay(todayEpochDay) }
    val completedToday = todayEpochDay in save.daily.completedDays

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(Neon.spaceGradient)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                    text = "每日挑战",
                    color = Neon.cyan,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.height(22.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "连续 ${save.daily.streak} 天",
                color = Neon.gold,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(20.dp))

            NeonPanel(modifier = Modifier.fillMaxWidth()) {
                MonthCalendar(month = YearMonth.from(today), today = today, completedDays = save.daily.completedDays)
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (completedToday) {
                Text(
                    text = "今日已完成 ✓",
                    color = Neon.mint,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = "开始今日挑战",
                    color = Neon.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            container.sfx.play(Sfx.UI_TAP)
                            container.haptics.tick()
                            onStart(GameMode.Daily(DailyLevel.forEpochDay(todayEpochDay)))
                        }
                        .background(Color(0x330A1030), RoundedCornerShape(14.dp))
                        .padding(vertical = 14.dp),
                )
            }
        }
    }
}

private val WEEKDAY_LABELS = listOf("日", "一", "二", "三", "四", "五", "六")

/** A simple, non-lazy 7-column month grid: weekday headers, then one row per week. */
@Composable
private fun MonthCalendar(month: YearMonth, today: LocalDate, completedDays: Set<Long>) {
    val firstOfMonth = month.atDay(1)
    // DayOfWeek.SUNDAY.value == 7; fold that to a 0-based, Sunday-first column index.
    val leadingBlanks = firstOfMonth.dayOfWeek.value % 7
    val daysInMonth = month.lengthOfMonth()

    val cells: List<Int?> = buildList {
        repeat(leadingBlanks) { add(null) }
        for (day in 1..daysInMonth) add(day)
        while (size % 7 != 0) add(null)
    }
    val weeks = cells.chunked(7)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${month.year} 年 ${month.monthValue} 月",
            color = Neon.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            for (label in WEEKDAY_LABELS) {
                Text(
                    text = label,
                    color = Neon.textDim,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        for (week in weeks) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (day in week) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day != null) {
                            val date = month.atDay(day)
                            val isCompleted = date.toEpochDay() in completedDays
                            val isToday = date == today
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                                    .background(
                                        color = if (isCompleted) Neon.gold.copy(alpha = 0.85f) else Color(0x14FFFFFF),
                                        shape = CircleShape,
                                    )
                                    .then(
                                        if (isToday) {
                                            Modifier.border(width = 1.5.dp, color = Neon.cyan, shape = CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "$day",
                                    color = when {
                                        isCompleted -> Neon.bgTop
                                        isToday -> Neon.cyan
                                        else -> Neon.textDim
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
