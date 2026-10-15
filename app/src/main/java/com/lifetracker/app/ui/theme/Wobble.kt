package com.lifetracker.app.ui.theme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * A rectangle whose four corners are each a slightly different oval, so a card looks drawn by hand rather than
 * stamped. Each corner is given as horizontal and vertical size in dp, like CSS `border-radius: a b c d / e f g h`.
 */
class WobbleShape(
    private val tl: Pair<Float, Float>,
    private val tr: Pair<Float, Float>,
    private val br: Pair<Float, Float>,
    private val bl: Pair<Float, Float>,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        fun corner(c: Pair<Float, Float>) = CornerRadius(c.first * density.density, c.second * density.density)
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                topLeftCornerRadius = corner(tl),
                topRightCornerRadius = corner(tr),
                bottomRightCornerRadius = corner(br),
                bottomLeftCornerRadius = corner(bl),
            ),
        )
    }
}

object Wobble {
    /** Cards, tiles, the menu bar. */
    val Card = WobbleShape(22f to 16f, 16f to 24f, 24f to 15f, 15f to 22f)

    /** Rows, buttons, inputs, small boxes. */
    val Small = WobbleShape(15f to 11f, 11f to 16f, 16f to 10f, 10f to 15f)

    /** Pills and chips. The sizes are larger than the pill, so each end is a slightly different curve. */
    val Pill = WobbleShape(99f to 70f, 80f to 99f, 99f to 80f, 70f to 99f)

    /** The floating Log button. */
    val Fab = WobbleShape(22f to 17f, 17f to 24f, 24f to 16f, 16f to 22f)

    /** The top of a pop-up sheet. */
    val Sheet = WobbleShape(34f to 26f, 26f to 34f, 0f to 0f, 0f to 0f)
}
