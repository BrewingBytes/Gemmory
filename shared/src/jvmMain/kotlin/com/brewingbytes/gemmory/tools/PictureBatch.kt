package com.brewingbytes.gemmory.tools

import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.ColorChoice
import com.brewingbytes.gemmory.converter.ConverterOptions
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.remote.CatalogCheck
import com.brewingbytes.gemmory.remote.RemoteCatalogs
import com.brewingbytes.gemmory.remote.RemoteCollection
import com.brewingbytes.gemmory.remote.RemoteKeys
import com.brewingbytes.gemmory.remote.RemotePicture
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Build-time only (T44, CEO E1): turns a folder of ChatGPT sheets and its `batch.csv` into dated remote pictures in one
 * signed publish. Each row names a sheet and a tile (1-6, reading order) and the picture made from it. The run reads
 * and checks every row, splits every sheet ([SheetSplitter]), stops at the content check until every row says yes,
 * then hands the whole batch to [RemotePictureTool.publish], which writes nothing unless all of it verifies. An
 * optional `collections.csv` beside it (T37: id, title, members separated by `;`) adds or replaces collections; a row
 * with `rewardOf` is that collection's keepsake. README → Publishing a batch of pictures.
 */
object PictureBatch {
    const val CSV = "batch.csv"

    /** Optional (T37): one collection per row. */
    const val COLLECTIONS_CSV = "collections.csv"

    val COLLECTION_COLUMNS = listOf("id", "title", "members")

    val REQUIRED = listOf("sheet", "tile", "id", "title", "size", "subject", "generatedOn", "contentChecked")

    /** Optional, with their defaults: colors `standard`, availableFrom none, locked `true`, focus `0.5`, rewardOf none. */
    val OPTIONAL = listOf("colors", "availableFrom", "locked", "focus", "prompt", "model", "rewardOf")

    const val DEFAULT_PROMPT = "prompts/sheet-2x3.md"

    const val DEFAULT_MODEL = "ChatGPT image generation (OpenAI)"

    const val AI_AUTHOR = "AI-generated for BrewingBytes"

    const val AI_LICENSE = "OpenAI terms assign output rights to the user; commercial use allowed (CEO review CONTENT-1). No real people or brands"

    class Row(
        val line: Int,
        val sheet: String,
        val tile: Int,
        val id: String,
        val title: String,
        val spec: String,
        val ratio: AspectRatio,
        val colors: ColorChoice,
        val availableFrom: LocalDate?,
        val locked: Boolean,
        val focus: Double,
        val subject: String,
        val generatedOn: LocalDate,
        val contentChecked: Boolean,
        val prompt: String,
        val model: String,
        /** A collection's keepsake (T37): hidden until that collection is earned; never dated. */
        val rewardOf: String? = null,
    )

    class Settings(
        val batchDir: File,
        /** The catalog folder, `pictures/v1/`. */
        val dir: File,
        /** Where sources and review PNGs go, `tools/remote/`. */
        val sourcesDir: File,
        val provenanceFile: File,
        val signingKey: File,
        val reservedIds: Set<String>,
        /** The founder's local date: every drop opens after it. */
        val today: LocalDate,
        val nowSeconds: Long,
        /** Where the review PNGs go while the content check isn't done (outside the repo's tracked files). */
        val previewDir: File,
        val trustedKeys: List<ByteArray> = RemoteKeys.PRODUCTION,
    )

    class Published(val pictures: List<RemotePicture>, val calendar: DropCalendar.Result)

    /** The content check stopped the batch: [unchecked] rows don't say yes yet; their previews are in the preview dir. */
    class NotChecked(val unchecked: List<String>) : IllegalStateException(
        "the content check isn't done for ${unchecked.joinToString()}: look at each review PNG against the check above, " +
            "then set contentChecked to yes on its row",
    )

    fun run(s: Settings): Published {
        val rows = read(File(s.batchDir, CSV).readText())
        checkRows(rows, s)
        val collections = File(s.batchDir, COLLECTIONS_CSV).takeIf { it.exists() }?.let { readCollections(it.readText()) }.orEmpty()
        val tiles = rows.groupBy { it.sheet }.mapValues { (sheet, group) ->
            val image = ImageIO.read(File(s.batchDir, sheet)) ?: error("cannot read sheet $sheet")
            try {
                SheetSplitter.split(image, group.first().ratio)
            } catch (e: SheetSplitter.SplitError) {
                throw IllegalArgumentException("$sheet: ${e.message}", e)
            }
        }
        val unchecked = rows.filter { !it.contentChecked }
        if (unchecked.isNotEmpty()) {
            s.previewDir.mkdirs()
            for (row in unchecked) {
                val size = CanvasSize.valueOf(row.spec.substringBefore('-').uppercase())
                val spec = BundledGridTool.SPECS.getValue(row.spec)
                val pattern = BundledGridTool.convertImage(tiles.getValue(row.sheet)[row.tile - 1], spec.copy(paletteCap = size.cap(row.colors)), row.focus, ConverterOptions.of(row.colors))
                ImageIO.write(BundledGridTool.preview(pattern), "png", File(s.previewDir, "${row.id}.review.png"))
            }
            throw NotChecked(unchecked.map { it.id })
        }
        val requests = rows.map { row ->
            RemotePictureTool.Request(
                dir = s.dir,
                id = row.id,
                title = row.title,
                source = File(s.sourcesDir, "${row.id}.jpg"),
                spec = row.spec,
                focus = row.focus,
                locked = row.locked,
                minAppVersion = null,
                addedAt = s.today,
                nowSeconds = s.nowSeconds,
                signingKey = s.signingKey,
                provenance = "`${row.id}.jpg`: ${row.subject}, tile ${row.tile} of `${s.batchDir.name}/${row.sheet}`, a 2×3 sheet by " +
                    "${row.model}, ${row.generatedOn}, prompt `${row.prompt}` | $AI_AUTHOR | $AI_LICENSE",
                provenanceFile = s.provenanceFile,
                reviewPng = File(s.sourcesDir, "${row.id}.review.png"),
                reservedIds = s.reservedIds,
                trustedKeys = s.trustedKeys,
                colors = row.colors,
                availableFrom = row.availableFrom,
                sourceImage = tiles.getValue(row.sheet)[row.tile - 1],
                rewardOf = row.rewardOf,
            )
        }
        val pictures = RemotePictureTool.publish(requests, collections)
        val catalog = RemoteCatalogs.check(
            File(s.dir, "catalog.json").readBytes(), File(s.dir, "catalog.json.sig").readBytes(), s.trustedKeys, Int.MAX_VALUE, null, s.reservedIds,
        ) as CatalogCheck.Ok
        // From tomorrow: a batch can't add today's drop, and the catalog's own check (CI) covers today.
        return Published(pictures, DropCalendar.check(catalog.catalog.pictures, s.today.plus(DatePeriod(days = 1))))
    }

    /** Checks that need no image: one aspect per sheet, each tile once, new sources, dates ahead. */
    private fun checkRows(rows: List<Row>, s: Settings) {
        val problems = ArrayList<String>()
        if (rows.isEmpty()) problems += "$CSV has no rows"
        for ((sheet, group) in rows.groupBy { it.sheet }) {
            if (!File(s.batchDir, sheet).exists()) problems += "$sheet isn't in ${s.batchDir}"
            if (group.map { it.ratio }.distinct().size > 1) problems += "$sheet mixes aspects: one aspect per sheet"
            group.groupBy { it.tile }.filterValues { it.size > 1 }.keys.forEach { problems += "$sheet tile $it is on two rows" }
        }
        rows.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { problems += "$it is on two rows" }
        for (row in rows) {
            if (File(s.sourcesDir, "${row.id}.jpg").exists()) problems += "line ${row.line}: ${row.id} already has a source; ids are never reused"
            if (row.availableFrom != null && row.availableFrom <= s.today) problems += "line ${row.line}: ${row.availableFrom} isn't after today (${s.today})"
            if (row.rewardOf != null && row.availableFrom != null) problems += "line ${row.line}: a keepsake (rewardOf) has no availableFrom"
        }
        require(problems.isEmpty()) { problems.joinToString("\n") }
    }

    /** Parses [text] (a header row, then one row per picture; fields may be "quoted", with "" for a quote). */
    fun read(text: String): List<Row> {
        val lines = csv(text)
        require(lines.isNotEmpty()) { "$CSV is empty" }
        val header = lines.first().map { it.trim() }
        (REQUIRED - header.toSet()).takeIf { it.isNotEmpty() }?.let { throw IllegalArgumentException("$CSV is missing columns $it") }
        (header - (REQUIRED + OPTIONAL).toSet()).takeIf { it.isNotEmpty() }?.let { throw IllegalArgumentException("$CSV has unknown columns $it") }
        val problems = ArrayList<String>()
        val rows = lines.drop(1).withIndex().filter { (_, f) -> f.any { it.isNotBlank() } }.mapNotNull { (i, fields) ->
            val line = i + 2
            val v = header.zip(fields.map { it.trim() } + List((header.size - fields.size).coerceAtLeast(0)) { "" }).toMap()
            runCatching { row(line, v) }.onFailure { problems += "line $line: ${it.message}" }.getOrNull()
        }
        require(problems.isEmpty()) { problems.joinToString("\n") }
        return rows
    }

    /** Parses `collections.csv` (T37): a header `id,title,members`, then one collection per row, members split by `;`. */
    fun readCollections(text: String): List<RemoteCollection> {
        val lines = csv(text)
        require(lines.isNotEmpty()) { "$COLLECTIONS_CSV is empty" }
        val header = lines.first().map { it.trim() }
        require(header == COLLECTION_COLUMNS) { "$COLLECTIONS_CSV columns must be ${COLLECTION_COLUMNS.joinToString(",")}, were ${header.joinToString(",")}" }
        val problems = ArrayList<String>()
        val out = lines.drop(1).withIndex().filter { (_, f) -> f.any { it.isNotBlank() } }.mapNotNull { (i, fields) ->
            val line = i + 2
            val (id, title, members) = (fields.map { it.trim() } + listOf("", "", "")).take(3)
            val list = members.split(';').map { it.trim() }.filter { it.isNotEmpty() }
            when {
                !RemoteCatalogs.ID.matches(id) -> problems += "line $line: id must match ${RemoteCatalogs.ID.pattern}"
                title.isBlank() -> problems += "line $line: title is empty"
                list.isEmpty() -> problems += "line $line: members is empty"
                else -> return@mapNotNull RemoteCollection(id, title, list)
            }
            null
        }
        require(problems.isEmpty()) { problems.joinToString("\n") }
        return out
    }

    private fun row(line: Int, v: Map<String, String>): Row {
        fun need(name: String) = v[name]?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("$name is empty")
        val spec = need("size")
        require(spec in BundledGridTool.SPECS && spec != "starter-3:4") { "size must be one of ${BundledGridTool.SPECS.keys - "starter-3:4"}, was $spec" }
        val tile = need("tile").toIntOrNull()?.takeIf { it in 1..6 } ?: throw IllegalArgumentException("tile must be 1-6")
        val subject = need("subject")
        require('|' !in subject) { "subject can't contain |" }
        return Row(
            line = line,
            sheet = need("sheet"),
            tile = tile,
            id = need("id"),
            title = need("title"),
            spec = spec,
            ratio = AspectRatio.entries.first { it.label == spec.substringAfter('-') },
            colors = v["colors"]?.takeIf { it.isNotEmpty() }?.let { c -> ColorChoice.entries.firstOrNull { it.param == c } ?: throw IllegalArgumentException("colors must be fewer, standard or more") } ?: ColorChoice.STANDARD,
            availableFrom = v["availableFrom"]?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() ?: throw IllegalArgumentException("availableFrom must be YYYY-MM-DD") },
            locked = when (v["locked"].orEmpty()) {
                "", "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("locked must be true or false")
            },
            focus = v["focus"]?.takeIf { it.isNotEmpty() }?.let { f -> f.toDoubleOrNull()?.takeIf { it in 0.0..1.0 } ?: throw IllegalArgumentException("focus must be 0-1") } ?: 0.5,
            subject = subject,
            generatedOn = runCatching { LocalDate.parse(need("generatedOn")) }.getOrNull() ?: throw IllegalArgumentException("generatedOn must be YYYY-MM-DD"),
            contentChecked = when (v["contentChecked"].orEmpty().lowercase()) {
                "yes" -> true
                "", "no" -> false
                else -> throw IllegalArgumentException("contentChecked must be yes or no")
            },
            prompt = v["prompt"]?.takeIf { it.isNotEmpty() } ?: DEFAULT_PROMPT,
            model = v["model"]?.takeIf { it.isNotEmpty() } ?: DEFAULT_MODEL,
            rewardOf = v["rewardOf"]?.takeIf { it.isNotEmpty() }?.also { require(RemoteCatalogs.ID.matches(it)) { "rewardOf must be a collection id" } },
        )
    }

    private fun csv(text: String): List<List<String>> {
        val out = ArrayList<List<String>>()
        var fields = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { fields += field.toString(); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    fields += field.toString(); field.clear()
                    out += fields
                    fields = ArrayList()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) { fields += field.toString(); out += fields }
        return out
    }

    /** Gradle passes `key=value` pairs (`publishPictureBatch` in shared/build.gradle.kts). */
    @JvmStatic
    fun main(args: Array<String>) {
        val a = args.associate { it.substringBefore('=') to it.substringAfter('=', "") }
        fun arg(name: String) = a[name]?.takeIf { it.isNotBlank() } ?: error("missing -P$name")
        val settings = Settings(
            batchDir = File(arg("batch")),
            dir = File(arg("dir")),
            sourcesDir = File(arg("reviewDir")),
            provenanceFile = File(arg("provenanceFile")),
            signingKey = File(arg("signingKey")),
            reservedIds = File(arg("bundled")).listFiles { f -> f.name.endsWith(".gmrg") }!!.map { it.name.removeSuffix(".gmrg") }.toSet(),
            today = LocalDate.parse(java.time.LocalDate.now().toString()),
            nowSeconds = System.currentTimeMillis() / 1000,
            previewDir = File(arg("previewDir")),
        )
        val published = try {
            run(settings)
        } catch (e: NotChecked) {
            println("Before publishing, check every picture:")
            RemotePictureTool.CONTENT_CHECK.forEach { println("  - $it") }
            e.unchecked.forEach { println("  ${File(settings.previewDir, "$it.review.png")}") }
            throw e
        }
        published.pictures.forEach { p ->
            println("published ${p.id} (${p.width}×${p.height}, ${p.colors} colors, min app ${p.minAppVersion}${p.availableFrom?.let { ", opens $it" }.orEmpty()})")
        }
        println(DropCalendar.message(published.calendar))
        println("verified with the app's checks; commit pictures/v1/, tools/remote/ and the batch folder, then open a PR")
    }
}
