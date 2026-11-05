package com.clarity.finance.ui.pilot

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display only. Never used for input parsing, storage, comparison or totals. */
object PresentationFormat {
    fun amount(canonical: String): String {
        val value = BigDecimal(canonical)
        val formatter = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.UK))
        return (if (canonical.startsWith("+")) "+" else "") + formatter.format(value)
    }
    fun minor(value: Long): String = amount(BigDecimal.valueOf(value, 2).toPlainString())
    fun month(iso: String): String = YearMonth.parse(iso).format(DateTimeFormatter.ofPattern("MMMM uuuu", Locale.UK))
    fun date(iso: String): String = runCatching {
        LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM uuuu", Locale.UK))
    }.getOrDefault(iso) // Invalid/unavailable source values remain visible, never fabricated.

    fun serverSavedAt(timestamp: com.google.firebase.Timestamp?, pending: Boolean = false): String = when {
        pending -> "Server-saved time pending"
        timestamp == null -> "Server-saved time unavailable"
        else -> "Server saved: " + DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm", Locale.UK)
            .withZone(ZoneId.of("Asia/Colombo"))
            .format(Instant.ofEpochSecond(timestamp.seconds, timestamp.nanoseconds.toLong())) + " (Colombo)"
    }

    const val expenseTimesExplanation = "Expenses are ordered by payment date, newest first. Within a date, the most recently saved to the server appears first. " +
        "Server-saved time is the original server confirmation, not the payment date or last edit. An offline entry shows when it later reaches the server, not when you tapped Save. " +
        "Unknown or pending times are labelled; they are never guessed. Reporting still uses the payment date."
}
