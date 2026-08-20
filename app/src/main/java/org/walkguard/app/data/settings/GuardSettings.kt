package org.walkguard.app.data.settings

import org.walkguard.app.core.model.GuardMode

data class GuardSettings(
    val guardEnabled: Boolean,
    val globalMode: GuardMode,
    val pauseUntilEpochMs: Long,
    val warningTitle: String,
    val warningMessage: String,
    /** Hide this app's task cards from system Recents (GKD excludeFromRecents). */
    val excludeFromRecents: Boolean
) {
    companion object {
        val Default = GuardSettings(
            guardEnabled = false,
            globalMode = GuardMode.RAGE,
            pauseUntilEpochMs = 0L,
            warningTitle = "",
            warningMessage = "",
            excludeFromRecents = false
        )
    }
}
