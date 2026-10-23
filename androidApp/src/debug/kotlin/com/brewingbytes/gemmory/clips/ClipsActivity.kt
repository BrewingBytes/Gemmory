package com.brewingbytes.gemmory.clips

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.brewingbytes.gemmory.ui.GemmoryTheme

/** The founder clip tool (T47), from Settings → Clips in debug builds. Declared only in the debug manifest. */
class ClipsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GemmoryTheme { ClipsScreen(onBack = ::finish) } }
    }
}
