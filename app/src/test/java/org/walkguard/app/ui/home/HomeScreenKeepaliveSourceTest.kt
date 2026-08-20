package org.walkguard.app.ui.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenKeepaliveSourceTest {
    @Test
    fun enableFailureDoesNotRollbackGuardEnabled() {
        val source = sourceFile("main/java/org/walkguard/app/ui/home/HomeScreen.kt").readText()

        val enableBranch = source.indexOf("if (enabled)")
        assertTrue(enableBranch >= 0)
        val elseBranch = source.indexOf("} else {", enableBranch)
        assertTrue(elseBranch > enableBranch)
        val enableBody = source.substring(enableBranch, elseBranch)

        assertTrue(enableBody.contains("setGuardEnabled(true)"))
        assertTrue(enableBody.contains("GuardForegroundService.startSafely"))
        // Keep enabled on start failure so recovery/sync can retry.
        assertFalse(enableBody.contains("setGuardEnabled(false)"))

        val disableBody = source.substring(elseBranch)
        assertTrue(disableBody.contains("setGuardEnabled(false)"))
        assertTrue(disableBody.contains("stopService"))
    }

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File("src/$relativePath"),
            File("app/src/$relativePath")
        )
        return candidates.first { it.exists() }
    }
}
