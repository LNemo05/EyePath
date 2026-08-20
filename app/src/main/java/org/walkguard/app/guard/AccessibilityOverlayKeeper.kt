package org.walkguard.app.guard

import android.content.Context.WINDOW_SERVICE
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.accessibilityservice.AccessibilityService

/**
 * GKD-style 1×1 [TYPE_ACCESSIBILITY_OVERLAY] keepalive view.
 *
 * Port of `A11yService.useAliveOverlayView` (no visual UX impact; OEM stickiness only).
 */
object AccessibilityOverlayKeeper {
    private var aliveView: View? = null

    fun attach(service: AccessibilityService) {
        removeView(service)
        val tempView = View(service)
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags =
                flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            gravity = Gravity.START or Gravity.TOP
            width = 1
            height = 1
            packageName = service.packageName
        }
        try {
            // Some devices throw android.view.WindowManager$BadTokenException.
            windowManager(service).addView(tempView, lp)
            aliveView = tempView
        } catch (_: Throwable) {
            aliveView = null
        }
    }

    fun detach(service: AccessibilityService) {
        removeView(service)
    }

    private fun removeView(service: AccessibilityService) {
        val view = aliveView ?: return
        try {
            windowManager(service).removeView(view)
        } catch (_: Throwable) {
            // View may already be detached after process/service teardown.
        }
        aliveView = null
    }

    private fun windowManager(service: AccessibilityService): WindowManager {
        return service.getSystemService(WINDOW_SERVICE) as WindowManager
    }
}
