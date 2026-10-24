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
package org.meshtastic.feature.map

import org.meshtastic.core.model.Node
import kotlin.math.abs

/**
 * Where a node's marker sits when other nodes report exactly the same position.
 *
 * @param index 0 is drawn on top, on the true position. 1 until [MapNodeStacks.MAX_VISIBLE] peek out from behind it;
 *   the rest sit underneath it.
 * @param count how many nodes share the position.
 */
data class NodeStackSlot(val index: Int, val count: Int) {

    /** Only the top marker of a stack too big to show in full carries the count. */
    val showsCount: Boolean
        get() = index == 0 && count > MapNodeStacks.MAX_VISIBLE

    /**
     * A fraction under 1 to add to a marker's priority so the top of a stack draws above the rest of it, without ever
     * lifting it past a node of a higher priority.
     */
    val drawRank: Float
        get() = (MapNodeStacks.MAX_VISIBLE - minOf(index, MapNodeStacks.MAX_VISIBLE)) * DRAW_RANK_STEP

    private companion object {
        const val DRAW_RANK_STEP = 0.1f
    }
}

/**
 * Nodes reporting the same position, and how to draw them so the stack is visible.
 *
 * Firmware position precision rounds every node in an area onto the same grid point, so a busy site is mostly nodes
 * sharing exact coordinates. Drawn as they are, each stack looks like a single node. Here every member of a stack gets
 * a slot: the first stays on the true position and draws on top, the next few are nudged a few dp out from behind it,
 * and the rest hide under the first. The nudge is in screen units, not metres, so no marker moves on the ground and the
 * spread is the same at every zoom.
 *
 * Renderer-independent so both map engines draw a stack the same way.
 */
object MapNodeStacks {

    /** How many markers of one stack are visible at once. A bigger stack also shows its count. */
    const val MAX_VISIBLE = 4

    /** Where slots 1 to 3 peek out from, in dp, positive right and down. Slot 0 sits on the true position. */
    private val PEEK_OFFSETS_DP = listOf(-7f to -6f, 7f to -6f, 0f to 7f)

    /** The furthest any slot is drawn from the true position along either axis, in dp. */
    val MAX_PEEK_DP: Float = PEEK_OFFSETS_DP.maxOf { (dx, dy) -> maxOf(abs(dx), abs(dy)) }

    /** Every stack in [nodes], grouped once for both the markers and the tap. */
    fun group(nodes: List<Node>, myNodeNum: Int?): NodeStacks {
        val slots = mutableMapOf<Int, NodeStackSlot>()
        val members = mutableMapOf<Int, List<Node>>()
        stacks(nodes, myNodeNum).forEach { stack ->
            stack.forEachIndexed { index, node ->
                slots[node.num] = NodeStackSlot(index = index, count = stack.size)
                members[node.num] = stack
            }
        }
        return NodeStacks(slots = slots, members = members)
    }

    /** How far this slot's marker is drawn from the true position, in dp, positive right and down. */
    fun offsetDp(slot: NodeStackSlot): Pair<Float, Float> = PEEK_OFFSETS_DP.getOrNull(slot.index - 1) ?: (0f to 0f)

    /**
     * Groups on the integer position the node reported rather than a distance, because that is what precision rounding
     * produces: identical coordinates. A node can reach the map twice, so each stack is de-duplicated by number.
     */
    private fun stacks(nodes: List<Node>, myNodeNum: Int?): List<List<Node>> = nodes
        .filter { it.validPosition != null }
        .groupBy { it.position.latitude_i to it.position.longitude_i }
        .values
        .map { stack -> stack.distinctBy { it.num }.sortedWith(topFirst(myNodeNum)) }
        .filter { it.size > 1 }

    /** This node, then favorites, then whoever was heard from most recently. The number only breaks ties. */
    private fun topFirst(myNodeNum: Int?): Comparator<Node> = compareByDescending<Node> { it.num == myNodeNum }
        .thenByDescending { MapNodePolicy.priorityOf(it, myNodeNum) }
        .thenByDescending { it.lastHeard }
        .thenBy { it.num }
}

/**
 * The stacks in a node list. Nodes standing alone are in neither map.
 *
 * @param slots each stacked node's slot, keyed by node number.
 * @param members each stacked node's whole stack, top first, keyed by node number.
 */
data class NodeStacks(val slots: Map<Int, NodeStackSlot>, val members: Map<Int, List<Node>>) {
    companion object {
        val NONE = NodeStacks(slots = emptyMap(), members = emptyMap())
    }
}
