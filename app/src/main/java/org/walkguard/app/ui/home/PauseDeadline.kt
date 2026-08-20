package org.walkguard.app.ui.home

sealed interface PauseDeadlineResult {
    data class Valid(val deadlineEpochMs: Long) : PauseDeadlineResult
    data object Invalid : PauseDeadlineResult
    data object TooLarge : PauseDeadlineResult
}

fun calculatePauseDeadline(input: String, nowEpochMs: Long): PauseDeadlineResult {
    if (input.isEmpty() || input.any { !it.isDigit() }) return PauseDeadlineResult.Invalid

    val minutes = input.toLongOrNull() ?: return PauseDeadlineResult.TooLarge
    if (minutes <= 0L) return PauseDeadlineResult.Invalid
    if (minutes > Long.MAX_VALUE / MILLIS_PER_MINUTE) return PauseDeadlineResult.TooLarge

    val durationMs = minutes * MILLIS_PER_MINUTE
    if (nowEpochMs > Long.MAX_VALUE - durationMs) return PauseDeadlineResult.TooLarge

    return PauseDeadlineResult.Valid(nowEpochMs + durationMs)
}

private const val MILLIS_PER_MINUTE = 60_000L
