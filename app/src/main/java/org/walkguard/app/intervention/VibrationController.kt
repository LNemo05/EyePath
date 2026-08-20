package org.walkguard.app.intervention

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class VibrationController(private val context: Context) : WarningVibrator {
    override fun vibrateWarning() {
        val effect = VibrationEffect.createWaveform(longArrayOf(0L, 250L, 120L, 250L), -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(effect)
            }
        }
    }
}
