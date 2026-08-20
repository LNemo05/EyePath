package org.walkguard.app.update

/**
 * Public GitHub identity used by About + update checks.
 *
 * Product / store name: EyePath. Repository slug: eyepath.
 * Owner: [GITHUB_OWNER].
 */
object WalkGuardLinks {
    /** GitHub username or org that owns the public repo. */
    const val GITHUB_OWNER = "LNemo05"

    /** Public repository name (URL slug). */
    const val GITHUB_REPO = "eyepath"

    val profileUrl: String get() = "https://github.com/$GITHUB_OWNER"

    val repoUrl: String get() = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"

    val releasesLatestUrl: String get() = "$repoUrl/releases/latest"

    val releasesApiUrl: String
        get() = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    val isConfigured: Boolean
        get() = GITHUB_OWNER.isNotBlank() &&
            !GITHUB_OWNER.startsWith("YOUR_", ignoreCase = true) &&
            GITHUB_REPO.isNotBlank()
}
