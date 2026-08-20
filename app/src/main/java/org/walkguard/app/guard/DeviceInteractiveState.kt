package org.walkguard.app.guard

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

data class DeviceInteractiveSnapshot(
    val screenOn: Boolean,
    val unlocked: Boolean
)

fun Context.readDeviceInteractiveState(): DeviceInteractiveSnapshot {
    val powerManager = getSystemService(PowerManager::class.java)
    val keyguardManager = getSystemService(KeyguardManager::class.java)
    val screenOn = powerManager?.isInteractive == true
    val unlocked = screenOn && keyguardManager?.isKeyguardLocked == false
    return DeviceInteractiveSnapshot(screenOn = screenOn, unlocked = unlocked)
}