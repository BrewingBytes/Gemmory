package com.brewingbytes.gemmory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.brewingbytes.gemmory.play.PlayScreen
import com.brewingbytes.gemmory.spike.SpikeApp
import com.brewingbytes.gemmory.spike.SpikeCanvases
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.ui.GemmoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GemmoryTheme {
                // Saved state, so a canvas reopens after rotation and after the process is killed in the background.
                // Navigation proper comes with #13.
                var openCanvas by rememberSaveable { mutableStateOf<String?>(null) }
                when (val id = openCanvas) {
                    null -> SpikeApp(onOpenCanvas = { openCanvas = it })
                    else -> PlayScreen(
                        ref = CanvasRef(CanvasKind.PHOTO, id),
                        title = SpikeCanvases.title(id),
                        onBack = { openCanvas = null },
                    )
                }
            }
        }
    }
}
