package com.mkdev.mkboardgames.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import kotlin.math.max

/**
 * Shared modal surfaces for every game. Keeping the window setup here prevents
 * new games from accidentally falling back to the platform AlertDialog theme.
 */
object StyledDialogs {
    fun showChoices(
        context: Context,
        title: String,
        subtitle: String,
        choices: List<ChessChoiceView.Choice>,
        heightDp: Float = 520f,
        gameLabel: String,
        headerSymbol: String = "♛",
        onCancel: (() -> Unit)? = null,
        onChoice: (Int, Dialog) -> Unit,
    ): Dialog {
        val view = ChessChoiceView(context, title, subtitle, choices, gameLabel, headerSymbol)
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener { onCancel?.invoke() }
        view.onChoiceSelected = { index ->
            onChoice(index, dialog)
        }
        dialog.setOnShowListener {
            val window = dialog.window ?: return@setOnShowListener
            val density = context.resources.displayMetrics.density
            val margin = (24f * density).toInt()
            val maxWidth = (420f * density).toInt()
            val availableHeightDp = context.resources.displayMetrics.heightPixels / density - 32f
            val contentHeightDp = max(heightDp, 178f + choices.size * 104f)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setWindowAnimations(0)
            window.attributes = window.attributes.apply { dimAmount = 0.72f }
            window.setLayout(
                minOf(context.resources.displayMetrics.widthPixels - margin * 2, maxWidth),
                (minOf(contentHeightDp, availableHeightDp) * density).toInt(),
            )
            window.setGravity(Gravity.CENTER)
        }
        dialog.show()
        return dialog
    }

    fun showRules(
        context: Context,
        gameName: String,
        rules: String,
        gameLabel: String,
        onDone: (() -> Unit)? = null,
    ): Dialog {
        val view = ChessRulesView(context, gameName, rules, gameLabel, if (gameLabel == "L U D O") "⚄" else "♛")
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnShowListener {
            val window = dialog.window ?: return@setOnShowListener
            val density = context.resources.displayMetrics.density
            val margin = (24f * density).toInt()
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setWindowAnimations(0)
            window.attributes = window.attributes.apply { dimAmount = 0.72f }
            window.setLayout(
                minOf(context.resources.displayMetrics.widthPixels - margin * 2, (420f * density).toInt()),
                (620f * density).toInt(),
            )
        }
        view.onDone = {
            onDone?.invoke()
            dialog.dismiss()
        }
        dialog.show()
        return dialog
    }

    fun choice(label: String, detail: String, symbol: String, accent: String) =
        ChessChoiceView.Choice(label, detail, symbol, Color.parseColor(accent))
}