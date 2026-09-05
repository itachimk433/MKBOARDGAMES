package com.mkdev.mkboardgames.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

/**
 * Shared modal surfaces for every game.
 *
 * These used to be Android Dialog windows. That made a choice-to-choice
 * transition depend on the window manager removing one window and adding the
 * next one, which briefly exposed the activity background. The game surfaces
 * now live in the activity's content FrameLayout, so every transition stays in
 * the same view hierarchy.
 */
object StyledDialogs {
    private var activeOverlay: FrameLayout? = null
    private var activeBackAction: (() -> Unit)? = null

    fun showChoices(
        context: Context,
        title: String,
        subtitle: String,
        choices: List<ChessChoiceView.Choice>,
        heightDp: Float = 520f,
        gameLabel: String,
        headerSymbol: String = "●",
        onCancel: (() -> Unit)? = null,
        fullScreen: Boolean = isFullScreenStyledGameLabel(gameLabel),
        onChoice: (Int, Dialog) -> Unit,
    ): Dialog {
        val view = ChessChoiceView(
            context,
            title,
            subtitle,
            choices,
            gameLabel,
            headerSymbol,
            fullScreenOverride = fullScreen,
        )
        val dialog = Dialog(context)
        view.onChoiceSelected = { index ->
            removeOverlay()
            onChoice(index, dialog)
        }
        showOverlay(
            context = context,
            content = view,
            fullScreen = fullScreen,
            contentHeightDp = max(heightDp, 178f + choices.size * 104f),
            cancelOnOutside = true,
            onBack = onCancel,
        )
        return dialog
    }

    private fun showOverlay(
        context: Context,
        content: View,
        fullScreen: Boolean,
        contentHeightDp: Float,
        cancelOnOutside: Boolean,
        onBack: (() -> Unit)?,
    ) {
        removeOverlay()
        val activity = context as? Activity
            ?: error("StyledDialogs requires an Activity context")
        val host = activity.findViewById<ViewGroup>(android.R.id.content)
            ?: error("Activity content host is unavailable")
        val density = context.resources.displayMetrics.density
        val overlay = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            setBackgroundColor(Color.TRANSPARENT)
        }
        val scrim = View(context).apply {
            setBackgroundColor(
                if (fullScreen) Color.TRANSPARENT else Color.argb(184, 0, 0, 0),
            )
            isClickable = cancelOnOutside
            if (cancelOnOutside) {
                setOnClickListener { dismissAndRun(onBack) }
            }
        }
        overlay.addView(
            scrim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val contentParams = if (fullScreen) {
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        } else {
            val margin = (24f * density).toInt()
            val width = min(
                context.resources.displayMetrics.widthPixels - margin * 2,
                (420f * density).toInt(),
            )
            val availableHeightDp = context.resources.displayMetrics.heightPixels / density - 32f
            FrameLayout.LayoutParams(
                width,
                (min(contentHeightDp, availableHeightDp) * density).toInt(),
                Gravity.CENTER,
            )
        }
        overlay.addView(content, contentParams)
        host.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        activeOverlay = overlay
        activeBackAction = onBack
    }

    private fun dismissAndRun(action: (() -> Unit)?) {
        removeOverlay()
        action?.invoke()
    }

    private fun removeOverlay() {
        activeOverlay?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
        activeOverlay = null
        activeBackAction = null
    }

    /** Activities call this before their normal game-level back handling. */
    fun handleBackPressed(): Boolean {
        val action = activeBackAction ?: return false
        dismissAndRun(action)
        return true
    }

    fun showRules(
        context: Context,
        gameName: String,
        rules: String,
        gameLabel: String,
        onDone: (() -> Unit)? = null,
    ): Dialog {
        val normalizedLabel = gameLabel.replace(" ", "")
        val headerSymbol = when {
            normalizedLabel.contains("DRAUGHTS") -> "●"
            normalizedLabel.contains("CONNECT") -> "●"
            normalizedLabel.contains("MANCALA") -> "●"
            normalizedLabel.contains("FOX") -> "🦊"
            gameLabel == "L U D O" -> "⚄"
            else -> "♛"
        }
        val view = ChessRulesView(context, gameName, rules, gameLabel, headerSymbol)
        val dialog = Dialog(context)
        view.onDone = {
            dismissAndRun(onDone)
        }
        showOverlay(
            context = context,
            content = view,
            fullScreen = isFullScreenStyledGameLabel(gameLabel),
            contentHeightDp = 620f,
            cancelOnOutside = false,
            onBack = onDone,
        )
        return dialog
    }

    fun choice(label: String, detail: String, symbol: String, accent: String) =
        ChessChoiceView.Choice(label, detail, symbol, Color.parseColor(accent))
}