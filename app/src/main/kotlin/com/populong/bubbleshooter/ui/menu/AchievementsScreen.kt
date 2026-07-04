package com.populong.bubbleshooter.ui.menu

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.populong.bubbleshooter.AppContainer
import com.populong.bubbleshooter.core.progress.AchievementDef
import com.populong.bubbleshooter.core.progress.Achievements
import com.populong.bubbleshooter.ui.theme.Neon
import com.populong.bubbleshooter.ui.theme.NeonPanel

/** Dim gray used for a locked achievement's badge, distinct from the gold used once unlocked. */
private val lockedBadgeColor = Color(0xFF5A6178)

/** Alpha applied to a locked achievement row's title/description so progress reads at a glance. */
private const val LOCKED_CONTENT_ALPHA = 0.5f

/**
 * The achievements screen: a scrollable list of every [Achievements.all] entry, each rendered as
 * a [NeonPanel] row. An unlocked achievement (its id present in
 * [com.populong.bubbleshooter.core.progress.SaveData.achievements]) shows a bright gold ★ badge
 * with full-brightness title/description; a locked one shows a dim gray ● badge with the text
 * dimmed to low alpha. A header line above the list reports how many of the total are unlocked.
 */
@Composable
fun AchievementsScreen(container: AppContainer, onBack: () -> Unit) {
    val save by container.save.save.collectAsState()
    val unlockedCount = remember(save) { Achievements.all.count { it.id in save.achievements } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(Neon.spaceGradient)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            NeonTopBar(container = container, title = "成就", onBack = onBack)

            Text(
                text = "已解锁 $unlockedCount / ${Achievements.all.size}",
                color = Neon.gold,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Bottom system-bar inset merged into the content padding (rather than a
                // Modifier.navigationBarsPadding() on the LazyColumn itself, which would clip
                // scrolled content) so the last row never sits under the gesture nav bar.
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 8.dp + navigationBarsBottomDp(),
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = Achievements.all, key = { it.id }) { def ->
                    AchievementRow(def = def, unlocked = def.id in save.achievements)
                }
            }
        }
    }
}

/** One achievement row: badge on the left, title over description on the right. */
@Composable
private fun AchievementRow(def: AchievementDef, unlocked: Boolean) {
    NeonPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (unlocked) "★" else "●",
                color = if (unlocked) Neon.gold else lockedBadgeColor,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(32.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (unlocked) 1f else LOCKED_CONTENT_ALPHA),
            ) {
                Text(
                    text = def.title,
                    color = Neon.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = def.description,
                    color = Neon.textDim,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
