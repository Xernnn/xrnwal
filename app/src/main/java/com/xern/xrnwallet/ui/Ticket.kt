package com.xern.xrnwallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val STUB = 52.dp // the tear-off strip at the bottom, below the notches

/**
 * A ticket: stamp-like bites along the top and bottom edges, a round notch on each side where the
 * stub tears off, and rounded corners.
 */
class TicketShape(private val corner: Dp, private val bite: Dp, private val step: Dp, private val notch: Dp, private val stub: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val body = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(corner.toPx()))) }
        val cuts = Path()
        val r = bite.toPx()
        val gap = step.toPx()
        // Bites start a corner in, so the corners stay round.
        var x = corner.toPx() + gap / 2
        while (x < size.width - corner.toPx()) {
            cuts.addOval(Rect(Offset(x, 0f), r))
            cuts.addOval(Rect(Offset(x, size.height), r))
            x += gap
        }
        val y = size.height - stub.toPx()
        cuts.addOval(Rect(Offset(0f, y), notch.toPx()))
        cuts.addOval(Rect(Offset(size.width, y), notch.toPx()))
        Outline.Generic(Path().apply { op(body, cuts, PathOperation.Difference) })
    }
}

/**
 * The Accounts tab's net-assets card as a ticket, with a colour drawing tucked into its corner, a dashed
 * line where the stub tears off and a barcode on the stub: the reference's card says "this is your
 * money" with a picture where a plain panel only says it with numbers.
 */
@Composable
fun NetAssetsTicket(content: @Composable BoxScope.() -> Unit) {
    val line = MaterialTheme.colorScheme.outline
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .fillMaxWidth()
            .clip(TicketShape(corner = 18.dp, bite = 4.dp, step = 14.dp, notch = 11.dp, stub = STUB))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 16.dp).rotate(-8f)) { Glyph("💰", 76.dp) }
        Box(Modifier.padding(bottom = STUB + 10.dp)) { content() }
        Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(STUB)) {
            // The tear line, through the notches.
            drawLine(
                line,
                Offset(14.dp.toPx(), 0f),
                Offset(size.width - 14.dp.toPx(), 0f),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
            // A barcode on the stub: fixed bar widths, so it never changes between draws.
            val bars = intArrayOf(2, 1, 1, 3, 1, 2, 1, 1, 2, 3, 1, 1, 2, 1, 3, 1, 2, 2, 1, 1, 3, 1, 2, 1)
            val unit = 1.6.dp.toPx()
            var x = size.width - 22.dp.toPx() - bars.sumOf { it + 1 } * unit
            val top = 14.dp.toPx()
            bars.forEach { w ->
                drawRect(ink, Offset(x, top), Size(w * unit, size.height - top - 12.dp.toPx()))
                x += (w + 1) * unit
            }
        }
    }
}
