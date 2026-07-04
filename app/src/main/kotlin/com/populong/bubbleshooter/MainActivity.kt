package com.populong.bubbleshooter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.populong.bubbleshooter.ui.App

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container = AppContainer(this)
        setContent { App(container) }
    }

    override fun onPause() {
        container.music.stop()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (container.musicEnabled.value) container.music.start()
    }

    override fun onDestroy() {
        container.release()
        super.onDestroy()
    }
}
