package org.walkguard.app.guard

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardRuntimeSyncSourceTest {
    @Test
    fun resumeNeverPersistsGuardEnabledFalseOnTransientFailure() {
        val source = sourceFile("main/java/org/walkguard/app/guard/GuardRuntimeSync.kt").readText()

        assertTrue(source.contains("resumePersistedGuardIfNeeded"))
        assertTrue(
            source.contains("startSafely") || source.contains("syncGuardState")
        )
        // Transient start/permission failures must not clear the user's enabled intent.
        assertFalse(source.contains("setGuardEnabled(false)"))
    }

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File("src/$relativePath"),
            File("app/src/$relativePath")
        )
        return candidates.first { it.exists() }
    }
}
