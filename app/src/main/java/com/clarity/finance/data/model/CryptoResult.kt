package com.clarity.finance.data.model

import com.google.firebase.Timestamp
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class CryptoSync { CONFIRMED, CACHED, PENDING, FAILED }

data class CryptoResult(
    val id: String,
    val direction: String,
    val amountMinor: Long,
    val resultDate: String,
    val note: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val lastOperationId: String = "",
    val sync: CryptoSync = CryptoSync.CONFIRMED,
    val error: String? = null,
    val deleting: Boolean = false
) {
    val signedMinor: Long get() = when (direction) {
        "gain" -> amountMinor
        "loss" -> Math.negateExact(amountMinor)
        else -> error("Invalid result direction")
    }
}

/** Reporting is based on the entered local date, never the upload timestamp. */
object CryptoReporting {
    val zone: ZoneId = ZoneId.of("Asia/Colombo")
    fun today(clock: Clock = Clock.systemUTC()): LocalDate = LocalDate.now(clock.withZone(zone))

    fun amount(input: String): Long {
        require(input.matches(Regex("[0-9]+(\\.[0-9]{1,2})?"))) {
            "Enter a positive LKR amount with at most two decimal places; no sign or exponent."
        }
        val minor = try { BigDecimal(input).movePointRight(2).longValueExact() }
        catch (_: ArithmeticException) { throw IllegalArgumentException("Amount exceeds the supported integer range.") }
        require(minor > 0L) { "Amount must be greater than zero." }
        return minor
    }

    fun validate(direction: String, amount: Long, date: String, note: String, today: LocalDate) {
        require(direction in listOf("gain", "loss")) { "Select Gain or Loss." }
        require(amount > 0) { "Amount must be greater than zero." }
        require(date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) { "Use YYYY-MM-DD for the result date." }
        val parsed = try { LocalDate.parse(date) }
        catch (_: Exception) { throw IllegalArgumentException("Enter a real calendar date.") }
        require(parsed.year in 1..9999 && !parsed.isAfter(today)) { "Result date cannot be in the future." }
        require(note.length <= 500) { "Note must be 500 characters or fewer." }
    }

    fun cutoff(year: Int, today: LocalDate): LocalDate {
        require(year in 1..today.year) { "Choose a current or past reporting year." }
        return if (year == today.year) today else LocalDate.of(year, 12, 31)
    }

    fun net(results: List<CryptoResult>, start: LocalDate, end: LocalDate): Long =
        results.asSequence().filter {
            it.sync in listOf(CryptoSync.CONFIRMED, CryptoSync.CACHED) &&
                it.resultDate >= start.toString() && it.resultDate <= end.toString()
        }.fold(0L) { sum, result -> Math.addExact(sum, result.signedMinor) }

    fun monthNet(results: List<CryptoResult>, month: YearMonth): Long = net(results, month.atDay(1), month.atEndOfMonth())
    fun money(minor: Long): String = (if (minor > 0) "+" else "") + BigDecimal.valueOf(minor, 2).toPlainString()
}
