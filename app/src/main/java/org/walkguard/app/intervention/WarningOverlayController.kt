package org.walkguard.app.intervention

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.walkguard.app.R

class WarningOverlayController(
    private val context: Context,
    private val windowManager: WindowManager? = context.getSystemService(WindowManager::class.java)
) : WarningOverlayPresenter {
    private var overlayView: View? = null

    override fun showWarning(
        title: String,
        message: String,
        onDismissForCurrentScreenCycle: () -> Unit
    ) {
        if (!Settings.canDrawOverlays(context)) {
            throw SecurityException(context.getString(R.string.error_overlay_not_granted))
        }

        dismissWithoutCallback()

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(235, 120, 0, 0))
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        val titleView = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
        }
        val messageView = TextView(context).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }
        val closeButton = Button(context).apply {
            text = context.getString(R.string.overlay_dismiss_screen_cycle)
            setOnClickListener {
                dismissWithoutCallback()
                onDismissForCurrentScreenCycle()
            }
        }

        content.addView(
            titleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        content.addView(
            messageView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        content.addView(
            closeButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val manager = windowManager ?: throw IllegalStateException("WindowManager service is unavailable")
        manager.addView(root, params)
        overlayView = root
    }

    override fun dismissWarning() {
        dismissWithoutCallback()
    }

    fun dismissWithoutCallback() {
        val current = overlayView ?: return
        overlayView = null
        try {
            windowManager?.removeView(current)
        } catch (_: IllegalArgumentException) {
            // The system may already have removed the overlay during lifecycle changes.
        }
    }
}
