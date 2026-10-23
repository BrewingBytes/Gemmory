package com.brewingbytes.gemmory.remote

import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.storage.AtomicFiles
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * What the canvas store needs of the remote catalog: the pictures ready to show and their verified grids. A dated drop
 * (T36) is in neither before its local date, so no caller can show or open it early.
 */
interface RemoteCatalogView {
    /** Catalog pictures (for this app version) whose grid is downloaded and whose date has come, in catalog order. */
    fun listed(): List<RemotePicture>

    /** [id]'s downloaded grid, checked against its catalog entry; null when it isn't listed or the file was refused. */
    fun grid(id: String): ByteArray?

    /** Local days since [id]'s drop date (T36), for Analytics; null for an undated or unlisted picture. */
    fun daysSinceAvailable(id: String): Long? = null

    /** [id] is an earned keepsake (T37): its canvas stays after the catalog withdraws it, started or not. */
    fun earned(id: String): Boolean = false

    /** The newest catalog collection listing picture [id] (T37), for the canvas events' `collection_id`. */
    fun collectionOf(id: String): String? = null

    /** The newest weekly event listing picture [id] (T38), for the canvas events' `event_id`. */
    fun eventOf(id: String): String? = null

    object None : RemoteCatalogView {
        override fun listed(): List<RemotePicture> = emptyList()

        override fun grid(id: String): ByteArray? = null
    }
}

/** How a check ended. */
enum class CheckOutcome { NOT_DUE, UPDATED, UNCHANGED, INCOMPLETE, OFFLINE, FAILED }

/** A remote-picture problem for Crashlytics: a code and an id, never a URL with user data (there is none). */
class RemotePictureException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Remote pictures (#43): a signed catalog of finished grids in the repo's `pictures/v1/` folder, checked once a day
 * while the Gallery shows, cached under [StorageLayout.REMOTE] (never backed up) and played like bundled pictures.
 *
 * Every catalog is checked ([RemoteCatalogs.check]: signature, format, rollback, ids) before anything is stored, and
 * every grid ([RemoteCatalogs.checkGrid]: SHA-256, file, CRC, shape) before it's written and again when it's read.
 * Grids download before the catalog is replaced; a grid the new catalog no longer lists is deleted (a started
 * picture keeps its own copy in its canvas). Refusals and HTTP errors go to Crashlytics; being offline doesn't.
 * All file work runs on [io], one call at a time.
 *
 * Dated drops (T36) are downloaded and verified ahead like any picture, then kept hidden until their local date:
 * [listed] and [grid] leave them out, and only the Tomorrow line reads tomorrow's ([tomorrow], [tomorrowGrid]).
 * A collection's keepsake (T37) is hidden the same way until [earned] names it.
 */
class RemotePictures internal constructor(
    filesDir: File,
    private val source: RemoteSource,
    private val keys: List<ByteArray>,
    private val appVersion: Int,
    /** Ids the app already uses (the bundled pictures): a catalog listing one is refused. */
    private val reservedIds: Set<String>,
    private val clock: DayClock,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val reporter: NonFatalReporter = NonFatalReporter.Logcat,
    private val files: AtomicFiles = AtomicFiles(),
    /** The keepsakes earned so far (T37, the picture unlocks): any other `rewardOf` entry stays hidden. */
    private val earned: () -> Set<String> = { emptySet() },
) : RemoteCatalogView {
    private val catalogFile = File(filesDir, StorageLayout.REMOTE_CATALOG)
    private val signatureFile = File(filesDir, StorageLayout.REMOTE_SIGNATURE)
    private val stateFile = File(filesDir, StorageLayout.REMOTE_STATE)
    private val gridsDir = File(filesDir, StorageLayout.REMOTE_GRIDS)

    /** What the gallery reads; replaced whole, so a reader never sees half a check. */
    private class Snapshot(val catalog: RemoteCatalog?, val ready: Set<String>, val state: RemoteState)

    @Volatile
    private var snapshot: Snapshot? = null

    private var job: Job? = null

    private val _updates = MutableStateFlow(0)

    /** Counts background checks that brought something new (a catalog, a grid or a withdrawal): the gallery reloads. */
    val updates: StateFlow<Int> = _updates.asStateFlow()

    private fun current(): Snapshot = snapshot ?: synchronized(this) { snapshot ?: load().also { snapshot = it } }

    /** Downloaded and verified, dated or not, hidden or not. */
    private fun stored(): List<RemotePicture> = current().let { s -> s.catalog?.pictures.orEmpty().filter { it.id in s.ready } }

    override fun listed(): List<RemotePicture> {
        val today = today()
        val earned = earned()
        return stored().filter { RemotePictureRules.isVisible(it.availableFrom, today) && CollectionRules.rewardShows(it, earned) }
    }

    /**
     * The verified catalog (T37): the last one accepted, or the cached one re-verified, so offline counts as fetched.
     * Null when this phone never had one: no collections, and none can complete.
     */
    fun catalog(): RemoteCatalog? = current().catalog

    /** Catalog pictures downloaded and verified, hidden or not: a collection member that isn't listed yet is "coming". */
    fun storedIds(): Set<String> = current().ready

    /**
     * Every downloaded and verified picture, future drops and unearned keepsakes included: the debug Clips screen
     * (T47) bypasses the date and earn gates, never the signature.
     */
    fun storedPictures(): List<RemotePicture> = stored()

    /** A [storedPictures] grid, checked against the signed catalog like [grid]. For the debug Clips screen (T47). */
    fun storedGrid(id: String): ByteArray? = stored().firstOrNull { it.id == id }?.let(::verifiedGrid)

    /** A keepsake was copied into its canvas (T37, plan-design-review D25): New from today for 7 days. */
    fun earnedToday(id: String) {
        scope.launch(io) { update(current().let { Snapshot(it.catalog, it.ready, it.state.seenOn(id, today())) }) }
    }

    override fun grid(id: String): ByteArray? = listed().firstOrNull { it.id == id }?.let(::verifiedGrid)

    override fun earned(id: String): Boolean = id in earned()

    override fun collectionOf(id: String): String? = current().catalog?.let { CollectionRules.collectionOf(id, it.collections) }

    override fun eventOf(id: String): String? = current().catalog?.let { CollectionRules.eventOf(id, it.collections) }

    override fun daysSinceAvailable(id: String): Long? =
        listed().firstOrNull { it.id == id }?.availableFrom?.let { today().toEpochDays() - it.toEpochDays() }

    /** Today's drops (T36): free to start today without a video, beside Today's picture (eng D4, D9). */
    fun todaysDrops(): Set<String> {
        val today = today()
        return listed().filter { RemotePictureRules.isTodaysDrop(it.availableFrom, today) }.map { it.id }.toSet()
    }

    /** Every listed dated drop (T36), whatever its date: Today's picture prefers one on its own date only. */
    fun drops(): Set<String> = listed().filter { it.availableFrom != null }.map { it.id }.toSet()

    /** Tomorrow's drop (T36), stored and verified, still hidden: the Tomorrow line frosts it. Null when there's none. */
    fun tomorrow(): RemotePicture? {
        val today = today()
        return stored().firstOrNull { RemotePictureRules.isTomorrowsDrop(it.availableFrom, today) }
    }

    /** [tomorrow]'s grid, read only to draw its frost; nothing opens it before its date. */
    fun tomorrowGrid(): ByteArray? = tomorrow()?.let(::verifiedGrid)

    private fun verifiedGrid(picture: RemotePicture): ByteArray? {
        val id = picture.id
        val bytes = try {
            gridFile(id).takeIf { it.isFile }?.readBytes()
        } catch (e: IOException) {
            reporter.report(RemotePictureException("grid read $id", e))
            null
        } ?: return null
        return when (val check = RemoteCatalogs.checkGrid(picture, bytes)) {
            is GridCheck.Ok -> bytes
            is GridCheck.Refused -> {
                reporter.report(RemotePictureException("cached grid $id: ${check.reason.code}"))
                null
            }
        }
    }

    /** The gallery's "New" label: arrived in the last 7 days and not opened yet. */
    fun isNew(id: String): Boolean = current().state.isNew(id, today())

    /** The player opened [id] (its canvas or its locked sheet): its "New" label goes. */
    fun opened(id: String) {
        if (!isNew(id)) return
        scope.launch(io) { update(current().let { Snapshot(it.catalog, it.ready, it.state.opened(id)) }) }
    }

    /** Starts a check in the background when one is due (a day since the last success, an hour since the last try). */
    @Synchronized
    fun checkIfDue() {
        if (job?.isActive == true || !RemoteSchedule.isDue(current().state, clock.nowMillis())) return
        job = scope.launch { if (check() == CheckOutcome.UPDATED) _updates.update { it + 1 } }
    }

    /**
     * The evening reminder's check (T35, eng D5): when one is due, it joins the gallery's running check or starts one
     * that downloads the catalog and only today's drop grids, and gives up after [timeoutMs], cancelling it. A
     * cancelled check stores no catalog (grids first), and one that skipped grids isn't marked done, so the next
     * Gallery visit runs the full check. True when it gave up.
     */
    suspend fun checkForReminder(timeoutMs: Long = REMINDER_CHECK_MS): Boolean {
        val running = synchronized(this) {
            job?.takeIf { it.isActive } ?: if (!RemoteSchedule.isDue(current().state, clock.nowMillis())) {
                null
            } else {
                scope.launch {
                    val today = today()
                    if (check(grids = { RemotePictureRules.isTodaysDrop(it.availableFrom, today) }) == CheckOutcome.UPDATED) _updates.update { it + 1 }
                }.also { job = it }
            }
        } ?: return false
        if (withTimeoutOrNull(timeoutMs) { running.join() } != null) return false
        running.cancel()
        return true
    }

    /** Stops a running check: a canvas is opening, and nothing downloads during play. It retries on the next Gallery visit. */
    @Synchronized
    fun pause() {
        job?.cancel()
        job = null
    }

    /**
     * One check, now if due ([force] for tests). Cancellable between requests. Only grids [grids] accepts are
     * downloaded; a skipped one that isn't cached leaves the check incomplete.
     */
    suspend fun check(force: Boolean = false, grids: (RemotePicture) -> Boolean = { true }): CheckOutcome = withContext(io) {
        val start = current()
        val now = clock.nowMillis()
        if (!force && !RemoteSchedule.isDue(start.state, now)) return@withContext CheckOutcome.NOT_DUE
        var state = start.state.attempted(now)
        update(Snapshot(start.catalog, start.ready, state))

        val ifNoneMatch = RemoteSchedule.ifNoneMatch(state, start.catalog != null)
        val catalogBytes: ByteArray?
        val signatureBytes: ByteArray?
        val etag: String?
        val catalog: RemoteCatalog
        when (val fetched = source.fetch(CATALOG, ifNoneMatch, RemoteCatalogs.MAX_CATALOG_BYTES)) {
            Fetched.NotModified -> {
                catalog = start.catalog ?: return@withContext failed("catalog: 304 without a cached catalog")
                catalogBytes = null
                signatureBytes = null
                etag = state.etag
            }
            is Fetched.Body -> {
                coroutineContext.ensureActive()
                val sig = when (val s = source.fetch(SIGNATURE, null, SIGNATURE_MAX_BYTES)) {
                    is Fetched.Body -> s.bytes
                    else -> return@withContext problem("signature", s)
                }
                catalog = when (val c = RemoteCatalogs.check(fetched.bytes, sig, keys, appVersion, state.issuedAt, reservedIds)) {
                    is CatalogCheck.Ok -> c.catalog
                    is CatalogCheck.Refused -> return@withContext failed("catalog: ${c.reason.code}")
                }
                catalogBytes = fetched.bytes
                signatureBytes = sig
                etag = fetched.etag
            }
            else -> return@withContext problem("catalog", fetched)
        }

        // Grids first, so a stored catalog never lists a grid that isn't here yet.
        var complete = true
        var offline = false
        val ready = HashSet<String>()
        for (picture in catalog.pictures) {
            coroutineContext.ensureActive()
            if (cachedGridIsGood(picture)) {
                ready += picture.id
                continue
            }
            if (!grids(picture)) {
                complete = false
                continue
            }
            val outcome = when (val g = source.fetch(RemoteCatalogs.gridPath(picture.id), null, RemoteCatalogs.MAX_GRID_BYTES)) {
                is Fetched.Body -> when (val check = RemoteCatalogs.checkGrid(picture, g.bytes)) {
                    is GridCheck.Ok -> if (write(gridFile(picture.id), g.bytes, "grid ${picture.id}")) CheckOutcome.UPDATED else CheckOutcome.FAILED
                    is GridCheck.Refused -> failed("grid ${picture.id}: ${check.reason.code}")
                }
                else -> problem("grid ${picture.id}", g)
            }
            if (outcome == CheckOutcome.UPDATED) ready += picture.id else complete = false
            if (outcome == CheckOutcome.OFFLINE) offline = true
        }

        if (catalogBytes != null && signatureBytes != null) {
            gridsDir.mkdirs()
            val stored = write(signatureFile, signatureBytes, "signature") && write(catalogFile, catalogBytes, "catalog")
            if (!stored) {
                update(Snapshot(start.catalog, start.ready, state))
                return@withContext CheckOutcome.FAILED
            }
        }
        val gone = RemotePictureRules.withdrawn(gridIdsOnDisk(), catalog)
        gone.forEach { gridFile(it).delete() }

        if (complete) state = state.succeeded(now, today(), etag, catalog)
        update(Snapshot(catalog, ready, state))
        when {
            !complete && offline -> CheckOutcome.OFFLINE
            !complete -> CheckOutcome.INCOMPLETE
            catalogBytes == null && gone.isEmpty() && ready == start.ready -> CheckOutcome.UNCHANGED
            else -> CheckOutcome.UPDATED
        }
    }

    private fun today(): LocalDate = LocalDate.fromEpochDays(clock.today())

    private fun gridFile(id: String) = File(gridsDir, "$id.gmrg")

    private fun gridIdsOnDisk(): Set<String> =
        gridsDir.listFiles { f -> f.isFile && f.name.endsWith(".gmrg") }?.map { it.name.removeSuffix(".gmrg") }?.toSet().orEmpty()

    private fun cachedGridIsGood(picture: RemotePicture): Boolean {
        val file = gridFile(picture.id)
        if (!file.isFile) return false
        val bytes = try {
            file.readBytes()
        } catch (_: IOException) {
            return false
        }
        return RemoteCatalogs.checkGrid(picture, bytes) is GridCheck.Ok
    }

    private fun write(file: File, bytes: ByteArray, what: String): Boolean = try {
        file.parentFile?.mkdirs()
        files.write(file, bytes)
        true
    } catch (e: IOException) {
        val kind = if (AtomicFiles.isDiskFull(e)) "disk full" else "write failed"
        reporter.report(RemotePictureException("$what: $kind", e))
        false
    }

    private fun failed(message: String): CheckOutcome {
        reporter.report(RemotePictureException(message))
        return CheckOutcome.FAILED
    }

    private fun problem(what: String, fetched: Fetched): CheckOutcome = when (fetched) {
        is Fetched.Failed -> if (fetched.offline) {
            CheckOutcome.OFFLINE
        } else {
            reporter.report(RemotePictureException("$what: ${fetched.error.javaClass.simpleName}", fetched.error))
            CheckOutcome.FAILED
        }
        is Fetched.HttpError -> failed("$what: HTTP ${fetched.code}")
        Fetched.TooLarge -> failed("$what: too large")
        Fetched.NotModified -> failed("$what: unexpected 304")
        is Fetched.Body -> error("not a problem")
    }

    private fun update(next: Snapshot) {
        snapshot = next
        try {
            stateFile.parentFile?.mkdirs()
            files.write(stateFile, RemoteStateCodec.encode(next.state))
        } catch (e: IOException) {
            reporter.report(RemotePictureException("state: write failed", e))
        }
    }

    /** The cache as last stored: the catalog is re-verified (it's small), grids are checked when read. */
    private fun load(): Snapshot {
        val state = try {
            stateFile.takeIf { it.isFile }?.readBytes()?.let { RemoteStateCodec.decode(it) as? Decoded.Ok }?.value
        } catch (_: IOException) {
            null
        } ?: RemoteState()
        val catalog = try {
            if (catalogFile.isFile && signatureFile.isFile) {
                when (val c = RemoteCatalogs.check(catalogFile.readBytes(), signatureFile.readBytes(), keys, appVersion, null, reservedIds)) {
                    is CatalogCheck.Ok -> c.catalog
                    is CatalogCheck.Refused -> {
                        reporter.report(RemotePictureException("cached catalog: ${c.reason.code}"))
                        null
                    }
                }
            } else {
                null
            }
        } catch (e: IOException) {
            reporter.report(RemotePictureException("cached catalog: read failed", e))
            null
        }
        val onDisk = gridIdsOnDisk()
        val ready = catalog?.pictures.orEmpty().map { it.id }.filter { it in onDisk }.toSet()
        // No usable catalog: forget its ETag, so the next check downloads it whole.
        return Snapshot(catalog, ready, if (catalog == null) state.copy(etag = null) else state)
    }

    companion object {
        const val CATALOG = "catalog.json"
        const val SIGNATURE = "catalog.json.sig"
        private const val SIGNATURE_MAX_BYTES = 1024

        /** The reminder's whole run has 10 s (eng D8); the check gets 8 of them (eng D5). */
        const val REMINDER_CHECK_MS = 8_000L
    }
}
