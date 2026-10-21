package com.brewingbytes.gemmory.ads

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * One ad SDK initialize per process (T34): [beforeInitialize] (Unity's privacy.consent write) always runs first, so
 * the mediated Unity SDK never starts without the player's US-states choice. Every caller awaits the same start.
 */
class AdStartup(
    private val scope: CoroutineScope,
    private val beforeInitialize: suspend () -> Unit,
    private val initialize: (done: () -> Unit) -> Unit,
) {
    var ready = false
        private set
    private var starting: CompletableDeferred<Unit>? = null

    suspend fun start() {
        if (ready) return
        val pending = starting ?: CompletableDeferred<Unit>().also { done ->
            starting = done
            scope.launch {
                beforeInitialize()
                initialize {
                    ready = true
                    done.complete(Unit)
                }
            }
        }
        pending.await()
    }
}
