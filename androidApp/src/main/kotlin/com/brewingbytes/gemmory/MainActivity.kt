package com.brewingbytes.gemmory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brewingbytes.gemmory.completion.CompletionScreen
import com.brewingbytes.gemmory.completion.CompletionViewModel
import com.brewingbytes.gemmory.gallery.GalleryScreen
import com.brewingbytes.gemmory.gallery.GalleryViewModel
import com.brewingbytes.gemmory.nav.BackStack
import com.brewingbytes.gemmory.nav.Route
import com.brewingbytes.gemmory.photo.CropScreen
import com.brewingbytes.gemmory.photo.PhotoFlowViewModel
import com.brewingbytes.gemmory.photo.PhotoNav
import com.brewingbytes.gemmory.photo.PreviewScreen
import com.brewingbytes.gemmory.photo.SizeScreen
import com.brewingbytes.gemmory.play.PlayScreen
import com.brewingbytes.gemmory.settings.SettingsScreen
import com.brewingbytes.gemmory.ui.GemmoryTheme

class MainActivity : ComponentActivity() {
    private val photoFlow: PhotoFlowViewModel by viewModels()
    private val gallery: GalleryViewModel by viewModels()
    private val completion: CompletionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GemmoryTheme {
                // Saved state, so the screen survives rotation and the process being killed in the background.
                val stack = rememberSaveable(saver = BackStack.saver) { BackStack() }
                App(stack)
            }
        }
    }

    @Composable
    private fun App(stack: BackStack) {
        val flow by photoFlow.state.collectAsStateWithLifecycle()
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // Cancelled: back to the gallery with no message (design D4).
            if (uri != null) photoFlow.onPhotoPicked(uri)
        }
        val pickPhoto = {
            photoFlow.onDecodeMessageShown()
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        LaunchedEffect(flow.nav) {
            when (val nav = flow.nav) {
                null -> return@LaunchedEffect
                PhotoNav.OpenCrop -> stack.push(Route.Crop)
                is PhotoNav.Started -> {
                    // Back from the new canvas lands on My Photos, where its tile is.
                    gallery.selectTab(1)
                    stack.started(nav.ref)
                }
                PhotoNav.Lost -> stack.dropPhotoFlow()
            }
            photoFlow.onNavHandled()
        }

        val top = stack.top
        val inPhotoFlow = top == Route.Crop || top == Route.Size || top == Route.Preview
        LaunchedEffect(inPhotoFlow) {
            if (inPhotoFlow && !photoFlow.hasDraft) stack.dropPhotoFlow()
        }
        // System back always goes one step up (design D3); on the Gallery it leaves the app. The canvas handles
        // its own back so it can close its session first.
        BackHandler(enabled = top != Route.Gallery && top !is Route.Canvas) { stack.pop() }
        LaunchedEffect(top is Route.Completion) { if (top !is Route.Completion) completion.onLeft() }

        when (top) {
            Route.Gallery -> {
                LaunchedEffect(Unit) { if (!flow.decoding) photoFlow.clearDraft() }
                GalleryScreen(
                    vm = gallery,
                    decodeFailed = flow.decodeFailed,
                    onNewFromPhoto = pickPhoto,
                    onOpen = { tile -> stack.openTile(tile.ref, tile.done) },
                    onSettings = { stack.push(Route.Settings) },
                )
            }
            Route.Settings -> SettingsScreen(onBack = { stack.pop() })
            Route.Crop -> CropScreen(
                state = flow,
                onRatio = photoFlow::onRatio,
                onView = photoFlow::onCropView,
                onNext = { rect ->
                    photoFlow.onCropNext(rect)
                    stack.push(Route.Size)
                },
                onBack = { stack.pop() },
            )
            Route.Size -> SizeScreen(flow, photoFlow::onSize, onNext = { stack.push(Route.Preview) }, onBack = { stack.pop() })
            Route.Preview -> PreviewScreen(
                state = flow,
                onStart = photoFlow::start,
                onChooseAnother = {
                    stack.popToGallery()
                    pickPhoto()
                },
                onBack = { stack.pop() },
            )
            is Route.Canvas -> PlayScreen(
                ref = top.ref,
                onBack = { stack.pop() },
                onFinished = {
                    completion.onFinished(top.ref)
                    stack.finished(top.ref)
                },
            )
            is Route.Completion -> CompletionScreen(
                ref = top.ref,
                fresh = top.fresh,
                vm = completion,
                onDone = { stack.popToGallery() },
                onPlayAgain = { stack.playAgain(top.ref) },
            )
        }
    }
}
