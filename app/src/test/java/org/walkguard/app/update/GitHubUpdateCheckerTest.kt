package org.walkguard.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateCheckerTest {

    @Test
    fun normalizeVersionLabel_stripsVPrefix() {
        assertEquals("0.1.0", GitHubUpdateChecker.normalizeVersionLabel("v0.1.0"))
        assertEquals("1.2", GitHubUpdateChecker.normalizeVersionLabel("V1.2"))
    }

    @Test
    fun compareVersionNames_ordersSemver() {
        assertTrue(GitHubUpdateChecker.compareVersionNames("0.1.1", "0.1.0") > 0)
        assertTrue(GitHubUpdateChecker.compareVersionNames("0.1.0", "0.1.1") < 0)
        assertEquals(0, GitHubUpdateChecker.compareVersionNames("1.0.0", "1.0.0"))
        assertTrue(GitHubUpdateChecker.compareVersionNames("1.2.10", "1.2.9") > 0)
    }

    @Test
    fun parseVersionCodeFromBody_readsConventionLine() {
        val body = """
            versionCode: 3

            - fix keepalive
            """.trimIndent()
        assertEquals(3L, GitHubUpdateChecker.parseVersionCodeFromBody(body))
        assertNull(GitHubUpdateChecker.parseVersionCodeFromBody("no code here"))
    }

    @Test
    fun parseVersionCodeFromTag_onlyPureNumeric() {
        assertEquals(12L, GitHubUpdateChecker.parseVersionCodeFromTag("v12"))
        assertNull(GitHubUpdateChecker.parseVersionCodeFromTag("v0.1.0"))
    }

    @Test
    fun walkGuardLinks_isConfiguredForPublishedIdentity() {
        assertEquals("LNemo05", WalkGuardLinks.GITHUB_OWNER)
        assertEquals("eyepath", WalkGuardLinks.GITHUB_REPO)
        assertTrue(WalkGuardLinks.isConfigured)
        assertEquals("https://github.com/LNemo05", WalkGuardLinks.profileUrl)
        assertEquals("https://github.com/LNemo05/eyepath", WalkGuardLinks.repoUrl)
    }
}
