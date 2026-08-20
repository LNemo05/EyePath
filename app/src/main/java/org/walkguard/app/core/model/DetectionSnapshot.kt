package org.walkguard.app.core.model

data class DetectionSnapshot(
    val screenOn: Boolean,
    val unlocked: Boolean,
    val walking: Boolean,
    val foregroundPackage: String?
) {
    val triggerSatisfied: Boolean
        get() = screenOn && unlocked && walking && foregroundPackage != null
}
