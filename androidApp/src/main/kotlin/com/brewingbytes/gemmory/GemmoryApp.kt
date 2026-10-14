package com.brewingbytes.gemmory

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.brewingbytes.gemmory.ads.AdGateway
import com.brewingbytes.gemmory.ads.AdMobGateway
import com.brewingbytes.gemmory.ads.ConsentManager
import com.brewingbytes.gemmory.ads.Interstitials
import com.brewingbytes.gemmory.ads.Monetization
import com.brewingbytes.gemmory.ads.PictureUnlocks
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.settings.AppSettings
import com.brewingbytes.gemmory.timelapse.EncodeManager
import com.brewingbytes.gemmory.timelapse.MediaCodecEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.TimeZone

class GemmoryApp : Application() {
    /** App-scoped, so a canvas's last write finishes even after its screen and ViewModel are gone. */
    lateinit var store: CanvasStore
        private set

    lateinit var settings: AppSettings
        private set

    /** App-scoped, so an encode keeps going while the process lives, whatever screen is showing (design D8). */
    lateinit var encodes: EncodeManager
        private set

    /** The daily allowance, the interstitial caps, picture unlocks and entitlements (#7; billing is #8). */
    lateinit var monetization: Monetization
        private set

    lateinit var consent: ConsentManager
        private set

    lateinit var ads: AdGateway
        private set

    lateinit var interstitials: Interstitials
        private set

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        encodes = EncodeManager(
            cacheDir = cacheDir,
            scope = scope,
            backend = { request, partialOf -> MediaCodecEncoder(this, request, partialOf) },
            canvasExists = { ref -> store.repository.dirOf(ref).isDirectory },
        )
        store = CanvasStore(
            filesDir = filesDir,
            scope = scope,
            writer = Dispatchers.IO.limitedParallelism(1),
            bundledGrid = { picture -> assets.open(picture.asset).use { it.readBytes() } },
            encodes = encodes,
        )
        monetization = Monetization(
            filesDir = filesDir,
            clock = DayClock(System::currentTimeMillis) { TimeZone.currentSystemDefault() },
            unlocks = PictureUnlocks(this),
            scope = scope,
            policy = Dispatchers.IO.limitedParallelism(1),
        )
        consent = ConsentManager(this)
        ads = AdMobGateway(this, consent, CoroutineScope(SupervisorJob() + Dispatchers.Main))
        interstitials = Interstitials(monetization, ads, { ref -> encodes.state(ref).value })
        // The first session ends on the first foreground after ≥ 30 min in the background (eng D6).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = monetization.onForeground()

            override fun onStop(owner: LifecycleOwner) = monetization.onBackground()
        })
    }
}
