package com.oklabs.oneui9launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View

/**
 * First-run setup, and the place the guide and the prompts send the person to. Each step shows whether it is done
 * and opens the exact Android screen for it. The app changes a setting only when the person taps a switch or a button here.
 */
class SetupActivity : Activity() {

    private lateinit var kit: SettingsKit
    private var focus = -1 // the step the person came here for; it is shown first

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kit = SettingsKit(this)
        focus = intent?.getIntExtra(EXTRA_STEP, -1) ?: -1
        render()
    }

    override fun onResume() {
        super.onResume()
        render() // a step can change while the person is in an Android screen
    }

    private fun render() {
        setContentView(buildPage())
    }

    private class Step(
        val id: Int,
        val title: String,
        val text: String,
        val action: String,
        val done: () -> Boolean,
        val onAction: () -> Unit
    )

    private fun buildPage(): View {
        val steps = mutableListOf<Step>()
        steps += Step(
            STEP_HOME,
            "Make it your Home app",
            "Press Home, choose Glassy Launcher and tap Always. After that the Home button opens this launcher.",
            "Set as Home app",
            { HomeKeeper.isDefault(this) }
        ) {
            startActivity(Intent(this, HomeRoleActivity::class.java))
        }
        steps += Step(
            STEP_PANEL,
            "Turn on the Quick panel",
            "Opens the panel from the top strip and the floating button, over any app. It needs \"Draw over other apps\".",
            "Turn on",
            { OverlayPanel.wanted(this) && OverlayPanel.canDraw(this) }
        ) {
            OverlayPanel.setWanted(this, true)
            if (!OverlayPanel.canDraw(this)) OverlayPanel.openPermissionScreen(this)
            OverlayPanel.sync(this)
        }
        if (!StoreBuild.noAccessibility(this)) {
            steps += Step(
                STEP_ACCESS,
                "Open the panel in every app (optional)",
                "Also opens it when you pull the status bar down in any app. It uses Android's Accessibility service and does not read your screen. " +
                    "If the switch is greyed out: Settings > Apps > Glassy Launcher, tap the three dots and choose \"Allow restricted settings\".",
                "Open Accessibility",
                { SystemPanel.serviceEnabled(this) }
            ) {
                getSharedPreferences("launcher", MODE_PRIVATE).edit().putBoolean(SystemPanel.PREF_ON, true).apply()
                SystemPanel.openAccessibilitySettings(this)
            }
        }
        steps += Step(
            STEP_NOTIFY,
            "Allow notifications",
            "Needed for the panel's quiet notification (tap it to open the panel) and for the notification list.",
            "Allow",
            { KeepAlive.notificationsOk(this) }
        ) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 50)
        }
        steps += Step(
            STEP_BATTERY,
            "Keep it running",
            "Phones by Huawei, Honor, Xiaomi, Vivo and others can stop background apps. Allow Auto-start and No restrictions for this app.",
            "Open battery settings",
            { KeepAlive.batteryOk(this) }
        ) {
            KeepAlive.openBatterySettings(this)
        }

        val ordered = if (focus >= 0) steps.sortedBy { if (it.id == focus) 0 else 1 } else steps

        return kit.page("Set up Glassy Launcher", { finish() }) { box ->
            kit.note(
                box,
                "Five quick steps. Each one opens the right phone screen, and a tick means it is already done. " +
                    "You can come back here any time from Settings > How to use."
            )
            for (s in ordered) {
                val done = s.done()
                kit.label(box, (if (done) "\u2714  " else "\u2718  ") + s.title)
                kit.note(box, s.text)
                kit.card(
                    box,
                    kit.row(
                        -1, 0,
                        if (done) "Change it" else s.action,
                        if (done) null else "Opens the Android screen for this step"
                    ) { s.onAction() }
                )
                if (s.id == STEP_BATTERY && KeepAlive.isHuaweiFamily()) {
                    kit.card(box, kit.row(-1, 0, "Open the auto-start screen", "On this phone it is in its own list") {
                        KeepAlive.openAutoLaunch(this)
                    })
                }
            }
            kit.label(box, "Finish")
            kit.card(
                box,
                kit.row(-1, 0, "Finish setup", "You can change everything later in Settings") {
                    markDone()
                    finish()
                }
            )
        }
    }

    private fun markDone() {
        getSharedPreferences("launcher", MODE_PRIVATE).edit().putBoolean(PREF_DONE, true).apply()
    }

    companion object {
        const val EXTRA_STEP = "setup_step"
        const val STEP_HOME = 0
        const val STEP_PANEL = 1
        const val STEP_ACCESS = 2
        const val STEP_NOTIFY = 3
        const val STEP_BATTERY = 4
        const val PREF_DONE = "setup_done"

        /** True once the person finished the setup. Until then it opens again on the next start. */
        fun isDone(c: Context): Boolean = c.getSharedPreferences("launcher", Context.MODE_PRIVATE).getBoolean(PREF_DONE, false)

        /** Opens the setup, with [step] shown first (or -1 for the usual order). */
        fun open(c: Context, step: Int = -1) {
            try {
                c.startActivity(Intent(c, SetupActivity::class.java).putExtra(EXTRA_STEP, step))
            } catch (e: Exception) {
                // no screen to show it on
            }
        }
    }
}
