package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Dedicated Ludo result surface.
 *
 * The generic choice dialog is intentionally not used here: standings can
 * contain four multi-line rows and need their own scrolling and action area.
 */
class LudoGameOverView(
    context: Context,
    private val winnerTitle: String,
    private val winnerSummary: String,
    private val rows: List<Row>,
) : FrameLayout(context) {
    data class Row(
        val place: Int,
        val playerName: String,
        val detail: String,
        val economyDetail: String?,
        val accentColor: Int,
        val isWinner: Boolean,
    )

    var onNewMatch: (() -> Unit)? = null
    var onMainMenu: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val textScale = resources.displayMetrics.scaledDensity.coerceAtMost(1.2f)

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Ludo game result. $winnerTitle"
        setBackgroundColor(Color.argb(198, 0, 0, 0))
        setOnClickListener { onDismiss?.invoke() }

        addView(
            buildCard(),
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ).apply {
                setMargins(dp(16f), dp(22f), dp(16f), dp(22f))
                gravity = Gravity.CENTER
            },
        )
    }

    private fun buildCard(): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(22f), dp(22f), dp(22f), dp(18f))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(54, 86, 184),
                    Color.rgb(25, 76, 126),
                    Color.rgb(12, 45, 78),
                ),
            ).apply {
                cornerRadius = dp(24f).toFloat()
                setStroke(dp(1f), Color.argb(170, 174, 218, 255))
            }
            isClickable = true
            elevation = dp(12f).toFloat()
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        header.addView(
            label(
                text = "MATCH COMPLETE",
                size = 12f,
                color = Color.rgb(246, 211, 122),
                bold = true,
                letterSpacing = 0.16f,
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        header.addView(
            label(
                text = "Ludo standings",
                size = 28f,
                color = Color.WHITE,
                bold = true,
            ).apply {
                setPadding(0, dp(4f), 0, 0)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        card.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val winnerPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16f), dp(13f), dp(16f), dp(13f))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.argb(92, 255, 255, 255),
                    Color.argb(43, 255, 255, 255),
                ),
            ).apply {
                cornerRadius = dp(16f).toFloat()
                setStroke(dp(1f), Color.argb(110, 255, 225, 140))
            }
        }
        winnerPanel.addView(
            label(
                text = "🏆  $winnerTitle",
                size = 21f,
                color = Color.WHITE,
                bold = true,
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        winnerPanel.addView(
            label(
                text = winnerSummary,
                size = 13f,
                color = Color.rgb(218, 234, 255),
                bold = false,
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(4f), 0, 0)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        card.addView(
            winnerPanel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(18f)
            },
        )

        card.addView(
            label(
                text = "FINAL RANKING",
                size = 11f,
                color = Color.rgb(246, 211, 122),
                bold = true,
                letterSpacing = 0.14f,
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(18f)
                bottomMargin = dp(8f)
            },
        )

        val rowsHost = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        rows.forEachIndexed { index, row ->
            rowsHost.addView(
                buildRow(row),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (index > 0) topMargin = dp(8f)
                },
            )
        }

        val scroll = ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                rowsHost,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        card.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val newMatch = actionButton(
            title = "NEW MATCH",
            subtitle = "Play again",
            accent = Color.rgb(242, 190, 102),
            darkText = true,
        ).apply {
            setOnClickListener { onNewMatch?.invoke() }
            contentDescription = "Start a new Ludo match"
        }
        val mainMenu = actionButton(
            title = "MAIN MENU",
            subtitle = "Choose a game",
            accent = Color.rgb(47, 104, 158),
            darkText = false,
        ).apply {
            setOnClickListener { onMainMenu?.invoke() }
            contentDescription = "Return to the main menu"
        }
        actions.addView(
            newMatch,
            LinearLayout.LayoutParams(0, dp(58f), 1f).apply {
                marginEnd = dp(5f)
            },
        )
        actions.addView(
            mainMenu,
            LinearLayout.LayoutParams(0, dp(58f), 1f).apply {
                marginStart = dp(5f)
            },
        )
        card.addView(
            actions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(14f)
            },
        )

        return card
    }

    private fun buildRow(row: Row): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), dp(9f), dp(10f), dp(9f))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.argb(if (row.isWinner) 78 else 45, 255, 255, 255),
                    Color.argb(25, 255, 255, 255),
                ),
            ).apply {
                cornerRadius = dp(12f).toFloat()
                setStroke(
                    dp(1f),
                    if (row.isWinner) {
                        Color.argb(150, 255, 220, 125)
                    } else {
                        Color.argb(55, 255, 255, 255)
                    },
                )
            }
        }

        val place = label(
            text = placeLabel(row.place),
            size = 16f,
            color = row.accentColor,
            bold = true,
        ).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(48, 255, 255, 255))
                setStroke(dp(1f), Color.argb(120, 255, 255, 255))
            }
        }
        container.addView(
            place,
            LinearLayout.LayoutParams(dp(42f), dp(42f)).apply {
                marginEnd = dp(11f)
            },
        )

        val text = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        text.addView(
            label(
                text = row.playerName,
                size = 16f,
                color = Color.WHITE,
                bold = row.isWinner,
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        text.addView(
            label(
                text = row.detail,
                size = 12f,
                color = Color.rgb(218, 234, 255),
                bold = false,
            ).apply {
                setPadding(0, dp(2f), 0, 0)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        row.economyDetail?.takeIf { it.isNotBlank() }?.let { economyDetail ->
            text.addView(
                label(
                    text = economyDetail,
                    size = 11f,
                    color = Color.rgb(174, 203, 231),
                    bold = false,
                ).apply {
                    setPadding(0, dp(2f), 0, 0)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        container.addView(
            text,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        return container
    }

    private fun actionButton(
        title: String,
        subtitle: String,
        accent: Int,
        darkText: Boolean,
    ): View {
        val textColor = if (darkText) Color.rgb(78, 42, 22) else Color.WHITE
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6f), 0, dp(6f), 0)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.argb(245, Color.red(accent), Color.green(accent), Color.blue(accent)),
                    Color.argb(220, Color.red(accent) - 24, Color.green(accent) - 24, Color.blue(accent) - 24),
                ),
            ).apply {
                cornerRadius = dp(13f).toFloat()
                setStroke(dp(1f), Color.argb(235, 255, 224, 155))
            }
            addView(
                label(title, 13f, textColor, true),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                label(subtitle, 10f, withAlpha(textColor, 190), false).apply {
                    setPadding(0, dp(2f), 0, 0)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun label(
        text: String,
        size: Float,
        color: Int,
        bold: Boolean,
        letterSpacing: Float = 0f,
    ): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(color)
            textSize = size * textScale
            gravity = Gravity.CENTER_VERTICAL
            typeface = Typeface.create(
                Typeface.DEFAULT,
                if (bold) Typeface.BOLD else Typeface.NORMAL,
            )
            this.letterSpacing = letterSpacing
        }

    private fun placeLabel(place: Int): String =
        when (place) {
            1 -> "1"
            2 -> "2"
            3 -> "3"
            else -> place.toString()
        }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun dp(value: Float): Int =
        (value * density).toInt()
}