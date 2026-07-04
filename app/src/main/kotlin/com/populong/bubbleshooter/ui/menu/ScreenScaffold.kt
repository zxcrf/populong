package com.populong.bubbleshooter.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.populong.bubbleshooter.AppContainer
import com.populong.bubbleshooter.audio.Sfx
import com.populong.bubbleshooter.ui.theme.Neon

/** Solid backdrop behind [NeonTopBar] so it stays legible over scrolling starfield content. */
private val TopBarBackdrop = Color(0xEE060B1E)

/**
 * The shared top bar for every non-menu screen: a real 48dp back-button hit target, a centered
 * title, and an optional [trailing] slot (e.g. a star count or a record count). Applies
 * [Modifier.statusBarsPadding] itself so screen content never has to reason about the status
 * bar / punch-hole cutout — callers simply place this as the first child of a [androidx.compose.foundation.layout.Column]
 * and let the rest of the screen lay out beneath it.
 *
 * Tapping back plays [Sfx.UI_TAP] and a haptic tick before invoking [onBack], matching every other
 * tappable affordance in the menu UI.
 */
@Composable
fun NeonTopBar(
    container: AppContainer,
    title: String,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TopBarBackdrop)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable {
                    container.sfx.play(Sfx.UI_TAP)
                    container.haptics.tick()
                    onBack()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "←", color = Neon.textPrimary, fontSize = 24.sp)
        }
        Text(
            text = title,
            color = Neon.cyan,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/**
 * The current bottom system-bar (navigation bar / gesture-nav inset) as a [Dp], for merging into a
 * `LazyColumn`'s `contentPadding` so its last item never sits under it. Scrollable, non-lazy
 * `Column`s should prefer `Modifier.navigationBarsPadding()` directly instead.
 */
@Composable
fun navigationBarsBottomDp(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
