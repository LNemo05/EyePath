package org.walkguard.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
abstract class WalkGuardRobolectricTest

object WalkGuardTestContext {
    val appContext: Context
        get() = ApplicationProvider.getApplicationContext()
}