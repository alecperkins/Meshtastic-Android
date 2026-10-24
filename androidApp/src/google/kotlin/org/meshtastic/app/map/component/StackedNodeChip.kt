/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.app.map.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.Node
import org.meshtastic.feature.map.MapNodeStacks
import org.meshtastic.feature.map.NodeStackSlot

/**
 * A node's chip, nudged to its place in a stack of nodes sharing one position.
 *
 * The marker image is centred on the position, so the nudge is made inside the image: transparent padding, more on one
 * side than the other, puts the chip off centre by exactly its slot's offset. The same padding leaves room for the
 * count on the top chip of a stack too big to show in full. A node standing alone is drawn as it always was.
 */
@Composable
fun StackedNodeChip(node: Node, slot: NodeStackSlot?, modifier: Modifier = Modifier) {
    if (slot == null) {
        PulsingNodeChip(node = node, modifier = modifier)
        return
    }
    val (dx, dy) = MapNodeStacks.offsetDp(slot)
    Box(
        modifier =
        modifier.padding(
            start = (STACK_MARGIN_DP + dx).dp,
            end = (STACK_MARGIN_DP - dx).dp,
            top = (STACK_MARGIN_DP + dy).dp,
            bottom = (STACK_MARGIN_DP - dy).dp,
        ),
    ) {
        PulsingNodeChip(node = node)
        if (slot.showsCount) {
            StackCountBadge(
                count = slot.count,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = BADGE_OVERHANG_DP.dp, y = -BADGE_OVERHANG_DP.dp),
            )
        }
    }
}

/** How many nodes share the spot, on the top chip's corner. */
@Composable
private fun StackCountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier =
        modifier
            .defaultMinSize(minWidth = BADGE_SIZE_DP.dp, minHeight = BADGE_SIZE_DP.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .border(1.dp, Color.White, CircleShape)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

private const val BADGE_SIZE_DP = 18
private const val BADGE_OVERHANG_DP = 8

/** Breathing room past the badge's overhang, so its border is not clipped at the image edge. */
private const val BADGE_CLEARANCE_DP = 2

/**
 * Transparent room on every side of a stacked chip. Derived from the largest slot offset, so no side's padding can go
 * negative whatever the offsets become, and from the badge's overhang.
 */
private val STACK_MARGIN_DP = maxOf(MapNodeStacks.MAX_PEEK_DP, BADGE_OVERHANG_DP.toFloat()) + BADGE_CLEARANCE_DP
