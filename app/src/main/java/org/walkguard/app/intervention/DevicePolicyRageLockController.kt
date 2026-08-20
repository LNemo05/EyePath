package org.walkguard.app.intervention

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import org.walkguard.app.guard.DeviceAdminLockReceiver

class DevicePolicyRageLockController(
    private val context: Context,
    private val devicePolicyManager: DevicePolicyManager? = context.getSystemService(DevicePolicyManager::class.java)
) : RageLockController {
    private val adminComponent = ComponentName(context, DeviceAdminLockReceiver::class.java)

    override fun isDeviceAdminActive(): Boolean {
        return devicePolicyManager?.isAdminActive(adminComponent) == true
    }

    override fun lockNow() {
        devicePolicyManager?.lockNow()
            ?: throw IllegalStateException("DevicePolicyManager service is unavailable")
    }
}
