package com.oklabs.oneui9launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Step 35: the Quick panel service (Accessibility) is stopped by the phone when the app's process dies (a crash, or the phone's "clean
 * background apps" on Vivo and others): Android then keeps it in the list as "switched on" but never starts it again until you switch it off and on.
 * This looks for exactly that state: switched on in the list, but not running. The launcher no longer switches the service off and on
 * itself (that rewrote the phone's Accessibility list). It says so once and the person switches it off and on by hand.
 */
object ServiceKeeper {

    enum class Result { RUNNING, NOT_ON, RESTARTED, NEEDS_USER, WAIT }

    private var lastTry = 0L
    private var told = false

    fun check(context: Context): Result {
        if (StoreBuild.noAccessibility(context)) return Result.NOT_ON // Step 8: the store build has no Accessibility service
        val mine = ComponentName(context, PanelAccessibilityService::class.java)
        val listed = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        } catch (e: Exception) {
            ""
        }
        val entries = listed.split(':').filter { it.isNotBlank() }
        val enabled = entries.any { ComponentName.unflattenFromString(it) == mine }
        if (!enabled) return Result.NOT_ON // switched off on purpose
        if (PanelAccessibilityService.instance != null) return Result.RUNNING
        val now = System.currentTimeMillis()
        if (now - lastTry < 20_000L) return Result.WAIT
        lastTry = now
        if (told) return Result.WAIT
        told = true
        return Result.NEEDS_USER
    }
}
