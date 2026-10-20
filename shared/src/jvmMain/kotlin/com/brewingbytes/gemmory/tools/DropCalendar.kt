package com.brewingbytes.gemmory.tools

import com.brewingbytes.gemmory.remote.CatalogCheck
import com.brewingbytes.gemmory.remote.RemoteCatalogs
import com.brewingbytes.gemmory.remote.RemoteKeys
import com.brewingbytes.gemmory.remote.RemotePicture
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import kotlin.system.exitProcess

/**
 * Build-time only (T44, CEO C4): how many days of daily drops are scheduled. The runway is the run of consecutive
 * dates from today that each have a drop, so a missed date ends it. The batch warns below [WARN_DAYS]; the daily CI
 * job (`.github/workflows/calendar.yml`) fails below [FAIL_DAYS], once the catalog has any dated picture.
 */
object DropCalendar {
    const val WARN_DAYS = 14

    const val FAIL_DAYS = 7

    sealed interface Result {
        /** No picture has a date yet: nothing to keep up. */
        data object NoDrops : Result

        data class Ok(val days: Int) : Result

        data class Warn(val days: Int) : Result

        data class Fail(val days: Int) : Result
    }

    fun runway(dates: Set<LocalDate>, today: LocalDate): Int {
        var days = 0
        while (today.plus(DatePeriod(days = days)) in dates) days++
        return days
    }

    fun check(pictures: List<RemotePicture>, today: LocalDate): Result {
        val dates = pictures.mapNotNull { it.availableFrom }.toSet()
        if (dates.isEmpty()) return Result.NoDrops
        val days = runway(dates, today)
        return when {
            days < FAIL_DAYS -> Result.Fail(days)
            days < WARN_DAYS -> Result.Warn(days)
            else -> Result.Ok(days)
        }
    }

    /** Today where a date opens first (UTC+14, Kiribati): the earliest phone already shows that date's drop. */
    fun today(nowMillis: Long): LocalDate =
        LocalDate.parse(Instant.ofEpochMilli(nowMillis).atOffset(ZoneOffset.ofHours(14)).toLocalDate().toString())

    fun message(result: Result): String = when (result) {
        Result.NoDrops -> "no dated drops in the catalog yet"
        is Result.Ok -> "${result.days} days of drops scheduled"
        is Result.Warn -> "only ${result.days} days of drops scheduled (fewer than $WARN_DAYS): publish the next batch"
        is Result.Fail -> "only ${result.days} days of drops scheduled (fewer than $FAIL_DAYS): players will see a day with no drop"
    }

    /** `<catalog dir>`: the CI check (`./gradlew :shared:checkDropCalendar`) on the published, verified catalog. */
    @JvmStatic
    fun main(args: Array<String>) {
        val dir = File(args.single())
        val catalog = File(dir, "catalog.json")
        val pictures = if (catalog.exists()) {
            val check = RemoteCatalogs.check(catalog.readBytes(), File(dir, "catalog.json.sig").readBytes(), RemoteKeys.PRODUCTION, Int.MAX_VALUE, null, emptySet())
            check(check is CatalogCheck.Ok) { "the published catalog doesn't verify: $check" }
            check.catalog.pictures
        } else {
            emptyList()
        }
        val result = check(pictures, today(System.currentTimeMillis()))
        println(message(result))
        if (result is Result.Fail) exitProcess(1)
    }
}
