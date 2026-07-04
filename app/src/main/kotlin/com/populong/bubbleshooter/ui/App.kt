package com.populong.bubbleshooter.ui

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.populong.bubbleshooter.AppContainer
import com.populong.bubbleshooter.core.mode.GameMode
import com.populong.bubbleshooter.game.GameScreen
import com.populong.bubbleshooter.ui.menu.MainMenuScreen

/** Top-level navigation state: which screen is currently shown. */
sealed interface Screen {
    /** The main menu / home screen. */
    data object Menu : Screen

    /** An active game run in [mode]. */
    data class Game(val mode: GameMode) : Screen
}

/**
 * The app's root composable. Owns screen-level navigation state and crossfades between the
 * main menu and an active game. [container] carries the process-lifetime services (sound,
 * haptics) shared by every screen.
 */
@Composable
fun App(container: AppContainer) {
    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }

    Crossfade(targetState = screen, label = "screen-crossfade") { current ->
        when (current) {
            is Screen.Menu -> MainMenuScreen(
                container = container,
                onPlay = { mode -> screen = Screen.Game(mode) },
            )

            is Screen.Game -> GameScreen(
                mode = current.mode,
                container = container,
                onExit = { screen = Screen.Menu },
            )
        }
    }
}
