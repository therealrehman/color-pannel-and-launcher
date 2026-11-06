package com.oklabs.oneui9launcher

import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Step 84: ONE button that turns the Quick panel on or off and, in the same tap, makes this launcher the phone's Home app.
 *
 * Why it is not silent: Android only lets the person change the Home app, in its own dialog (the Home role). So turning the panel ON
 * opens that dialog when this launcher is not the Home app yet. Turning the panel OFF only switches the panel off: Android has no way
 * for an app to give up the Home role by itself; to use another launcher the person picks it in the Home app settings.
 */
object PanelHomeSwitch {

    /** The button shows "on" while the Quick panel over other apps is switched on. */
    fun isOn(c: Context): Boolean = OverlayPanel.wanted(c)

    fun tap(c: Context) {
        if (OverlayPanel.wanted(c)) {
            OverlayPanel.setWanted(c, false)
            val home = if (HomeKeeper.isDefault(c)) "this launcher is still the Home app" else "another launcher is the Home app now"
            Toast.makeText(c, "Quick panel is off; " + home, Toast.LENGTH_SHORT).show()
            return
        }
        if (!OverlayPanel.canDraw(c)) {
            OverlayPanel.openPermissionScreen(c)
            Toast.makeText(c, "Allow \"Draw over other apps\" first, then tap again", Toast.LENGTH_LONG).show()
            return
        }
        OverlayPanel.setWanted(c, true)
        if (!HomeKeeper.isDefault(c)) {
            // the same tap asks Android to make this launcher the Home app (the system dialog; only the person can say yes)
            try {
                c.startActivity(Intent(c, HomeRoleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                // no Home app settings on this phone
            }
        } else {
            Toast.makeText(c, "Quick panel is on", Toast.LENGTH_SHORT).show()
        }
    }
}
