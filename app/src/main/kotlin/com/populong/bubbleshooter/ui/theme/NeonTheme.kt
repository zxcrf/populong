package com.populong.bubbleshooter.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.populong.bubbleshooter.core.grid.BubbleColor

/**
 * Deep-space neon design tokens shared by the game's Compose UI.
 *
 * The game chrome is deliberately not built on [androidx.compose.material3.MaterialTheme];
 * these are plain color/gradient constants consumed directly by composables.
 */
object Neon {
    /** Top stop of the background gradient. */
    val bgTop = Color(0xFF060B1E)

    /** Middle stop of the background gradient. */
    val bgMid = Color(0xFF0A1030)

    /** Bottom stop of the background gradient. */
    val bgDeep = Color(0xFF141A4A)

    /** Primary cyan accent. */
    val cyan = Color(0xFF4DE3FF)

    /** Secondary magenta accent. */
    val magenta = Color(0xFFE44DFF)

    /** Gold accent, used for scores/highlights. */
    val gold = Color(0xFFFFC94D)

    /** Mint accent, used for success/positive states. */
    val mint = Color(0xFF5DFFB0)

    /** Danger/warning accent. */
    val danger = Color(0xFFFF5C7A)

    /** Primary text color. */
    val textPrimary = Color(0xFFEAF6FF)

    /** Dimmed/secondary text color (60% alpha of [textPrimary]). */
    val textDim = Color(0x99EAF6FF)

    /** Vertical background gradient stops, top to bottom. */
    val spaceGradient: List<Color> = listOf(bgTop, bgMid, bgDeep)

    private val red = Color(0xFFFF5C7A)
    private val blue = Color(0xFF4D9FFF)
    private val green = Color(0xFF5DFFB0)
    private val yellow = Color(0xFFFFE45D)
    private val purple = Color(0xFFB44DFF)
    private val orange = Color(0xFFFF9A4D)

    /** Maps a core [BubbleColor] to its neon display color. */
    fun bubbleColor(color: BubbleColor): Color = when (color) {
        BubbleColor.RED -> red
        BubbleColor.BLUE -> blue
        BubbleColor.GREEN -> green
        BubbleColor.YELLOW -> yellow
        BubbleColor.PURPLE -> purple
        BubbleColor.ORANGE -> orange
    }
}

/**
 * A translucent, rounded-corner panel used as the base surface for menus and dialogs.
 *
 * Renders a dark fill, a thin cyan-tinted border, and a small outer padding that reads
 * as a subtle glow margin around the content.
 */
@Composable
fun NeonPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val shape = if (cornerRadius > 0.dp) RoundedCornerShape(cornerRadius) else RectangleShape
    Box(
        modifier = modifier
            .padding(4.dp)
            .background(color = Color(0xCC0A1030), shape = shape)
            .border(width = 1.dp, color = Neon.cyan.copy(alpha = 0.4f), shape = shape)
            .padding(16.dp),
    ) {
        content()
    }
}
