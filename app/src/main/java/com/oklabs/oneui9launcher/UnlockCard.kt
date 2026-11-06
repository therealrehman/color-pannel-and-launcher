package com.oklabs.oneui9launcher

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Step 84: the gradient-unlock card. A real card instead of a plain dialog or a toast: the colour that was tapped runs across the top with a
 * sparkle in its middle, then the title, the reason, and two buttons. "Watch ad" shows one rewarded ad (Huawei Ads, through [HuaweiAds]);
 * when the ad is finished the gradients stay unlocked for 12 hours. When no ad can be shown (no HMS Core, no fill) they unlock anyway.
 */
object UnlockCard {

    /** Shows the card for panel colour number [index]. [then] runs after the gradients are unlocked; [onClosed] when the card goes away. */
    fun show(activity: Activity, index: Int, then: () -> Unit, onClosed: () -> Unit = {}) {
        if (activity.isFinishing) return
        val c = activity
        val dialog = Dialog(c, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { _ -> onClosed() }

        val band = PanelTheme.wallpaperFor(index).colors
        val root = FrameLayout(c)
        root.setPadding(c.dp(26), c.dp(26), c.dp(26), c.dp(26))

        val card = LinearLayout(c)
        card.orientation = LinearLayout.VERTICAL
        card.background = GradientDrawable().apply {
            setColor(Color.argb(248, 18, 20, 28))
            cornerRadius = c.dpf(28f)
            setStroke(c.dp(1), Color.argb(46, 255, 255, 255))
        }
        card.elevation = c.dpf(18f)
        card.clipToOutline = true

        // The top band: the colour itself, with a soft sparkle in the middle.
        val top = FrameLayout(c)
        top.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, band).apply {
            val r = c.dpf(28f)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
        val sparkle = TextView(c)
        sparkle.text = "\u2726"
        sparkle.setTextColor(Color.WHITE)
        sparkle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
        sparkle.gravity = Gravity.CENTER
        sparkle.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(70, 255, 255, 255))
        }
        top.addView(sparkle, FrameLayout.LayoutParams(c.dp(72), c.dp(72), Gravity.CENTER))
        card.addView(top, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, c.dp(124)))

        val title = label(c, "Unlock gradient colours", 20f, Color.WHITE, bold = true)
        title.gravity = Gravity.CENTER
        card.addView(title, margins(c, top = 22, side = 22))

        val body = label(c, "Watch one short ad and every gradient colour of the Quick panel stays open for 12 hours. Solid colours are always free.", 15f, Color.argb(205, 255, 255, 255))
        body.gravity = Gravity.CENTER
        body.setLineSpacing(c.dpf(3f), 1f)
        card.addView(body, margins(c, top = 10, side = 26))

        val watch = pill(c, "Watch ad", band)
        card.addView(watch, margins(c, top = 22, side = 24))

        val later = label(c, "Not now", 15f, Color.argb(190, 255, 255, 255))
        later.gravity = Gravity.CENTER
        later.setPadding(0, c.dp(14), 0, 0)
        later.isClickable = true
        later.setOnClickListener { dialog.dismiss() }
        card.addView(later, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val note = label(c, "Closing the ad early does not unlock.", 12f, Color.argb(120, 255, 255, 255))
        note.gravity = Gravity.CENTER
        card.addView(note, margins(c, top = 8, side = 22))

        watch.setOnClickListener {
            if (!watch.isEnabled) return@setOnClickListener
            watch.isEnabled = false
            watch.text = "Loading ad\u2026"
            HuaweiAds.showRewarded(
                c,
                onRewarded = {
                    ThemeUnlock.grant(c)
                    dialog.dismiss()
                    then()
                },
                onUnavailable = {
                    ThemeUnlock.grant(c)
                    Toast.makeText(c, "No ad is available right now. Gradients are unlocked.", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    then()
                }
            )
        }

        root.addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.6f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        }
        card.alpha = 0f
        card.scaleX = 0.9f
        card.scaleY = 0.9f
        dialog.setOnShowListener { _ ->
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(0.8f)).start()
        }
        dialog.show()
    }

    private fun label(c: Context, text: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(c)
        t.text = text
        t.setTextColor(color)
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) t.typeface = android.graphics.Typeface.DEFAULT_BOLD
        return t
    }

    private fun pill(c: Context, label: String, band: IntArray): TextView {
        val t = TextView(c)
        t.text = label
        t.setTextColor(Color.WHITE)
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        t.typeface = android.graphics.Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.setPadding(c.dp(18), c.dp(14), c.dp(18), c.dp(14))
        t.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, band).apply { cornerRadius = c.dpf(26f) }
        t.elevation = c.dpf(6f)
        t.isClickable = true
        return t
    }

    private fun margins(c: Context, top: Int, side: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = c.dp(top)
        lp.leftMargin = c.dp(side)
        lp.rightMargin = c.dp(side)
        return lp
    }
}

/**
 * Step 84: the same card for places that have no screen of their own - the Quick panel (an overlay service cannot show a dialog) opens it
 * through this see-through activity.
 */
class CardActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val index = intent.getIntExtra(EXTRA_INDEX, 1)
        UnlockCard.show(this, index, then = {}, onClosed = { finish() })
    }

    companion object {
        const val EXTRA_INDEX = "card_index"

        fun launch(c: Context, index: Int) {
            try {
                c.startActivity(
                    Intent(c, CardActivity::class.java)
                        .putExtra(EXTRA_INDEX, index)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
                )
            } catch (e: Exception) {
                // no screen to show it on
            }
        }
    }
}
