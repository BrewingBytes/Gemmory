package com.brewingbytes.gemmory.play

import android.os.SystemClock
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasState
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.save.GridFile
import com.brewingbytes.gemmory.save.ViewState
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.CanvasRepository
import com.brewingbytes.gemmory.storage.LoadProblem
import com.brewingbytes.gemmory.storage.LoadResult
import com.brewingbytes.gemmory.storage.WriteResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * App-wide entry to canvas files. Every repository call runs on [writer], a single-thread dispatcher, and a canvas
 * is opened only after the previous session's last write has finished, so a canvas never has two writers.
 * [CanvasRepository.sweep] runs once when the store is made (app start), before anything is opened or created.
 */
class CanvasStore(
    filesDir: File,
    private val scope: CoroutineScope,
    private val writer: CoroutineDispatcher,
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    val repository = CanvasRepository(filesDir)
    private val swept: Job = scope.launch(writer) { repository.sweep() }
    private val closing = ConcurrentHashMap<CanvasRef, Job>()

    sealed interface Opened {
        class Ready internal constructor(
            val ref: CanvasRef,
            val grid: GridFile,
            val state: CanvasState,
            val view: ViewState?,
        ) : Opened

        /** "Can't open" (design D9); the gallery tile and sheet come with #13. */
        data class Damaged(val problem: LoadProblem?) : Opened
    }

    /** Loads a canvas: Loaded and RecoveredFromLastGood open it, Damaged doesn't. */
    suspend fun open(ref: CanvasRef): Opened {
        swept.join()
        closing[ref]?.join()
        return withContext(writer) {
            val (grid, log) = when (val r = repository.load(ref)) {
                is LoadResult.Loaded -> r.grid to r.log
                is LoadResult.RecoveredFromLastGood -> r.grid to r.log
                is LoadResult.Damaged -> return@withContext Opened.Damaged(r.problem)
            }
            // The codec already refused duplicate and out-of-range logs, so a replay can't fail here.
            val state = CanvasState.restore(grid.pattern, log) ?: return@withContext Opened.Damaged(null)
            Opened.Ready(ref, grid, state, repository.readView(ref, grid.pattern.palette.size))
        }
    }

    /** Starts the session of an opened canvas. Not suspending, so a cancelled caller can't leak a save loop. */
    fun start(opened: Opened.Ready): PlaySession {
        val ref = opened.ref
        val binding = opened.grid.binding
        val saver = SaveLoop(
            scope = CoroutineScope(scope.coroutineContext + writer),
            initialLog = opened.state.log(),
            nowMillis = nowMillis,
            writeSave = { log -> repository.writeSave(ref, binding, log) },
            writeView = { view -> repository.writeView(ref, view) },
        )
        return PlaySession(ref, opened.grid.pattern, opened.state, opened.view, saver) { lastWrite ->
            closing[ref] = lastWrite
            lastWrite.invokeOnCompletion { closing.remove(ref, lastWrite) }
        }
    }

    suspend fun create(ref: CanvasRef, pattern: Pattern): WriteResult {
        swept.join()
        return withContext(writer) { repository.create(ref, GridCodec.encode(pattern)) }
    }

    suspend fun list(kind: CanvasKind): List<String> {
        swept.join()
        return withContext(writer) { repository.list(kind) }
    }
}
