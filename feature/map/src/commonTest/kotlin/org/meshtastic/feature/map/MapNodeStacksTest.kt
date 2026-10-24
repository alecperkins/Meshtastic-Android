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
import org.meshtastic.proto.Position
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Nodes rounded onto the same position by firmware precision, and where each one's marker goes. */
class MapNodeStacksTest {

    private fun node(
        num: Int,
        latitudeI: Int = SPOT_LATITUDE_I,
        longitudeI: Int = SPOT_LONGITUDE_I,
        lastHeard: Int = 0,
        isFavorite: Boolean = false,
    ) = Node(
        num = num,
        position =
        Position.Builder()
            .also { wb ->
                wb.latitude_i = latitudeI
                wb.longitude_i = longitudeI
            }
            .build(),
        lastHeard = lastHeard,
        isFavorite = isFavorite,
    )

    @Test
    fun `nodes on exactly the same position form one stack`() {
        val slots = MapNodeStacks.group(listOf(node(1), node(2), node(3)), myNodeNum = null).slots

        assertEquals(setOf(1, 2, 3), slots.keys)
        assertTrue(slots.values.all { it.count == 3 })
        assertEquals(setOf(0, 1, 2), slots.values.map { it.index }.toSet())
    }

    @Test
    fun `a node standing alone gets no slot`() {
        val slots =
            MapNodeStacks.group(listOf(node(1), node(2, latitudeI = SPOT_LATITUDE_I + 1)), myNodeNum = null).slots

        assertTrue(slots.isEmpty())
    }

    @Test
    fun `nearby but different positions do not stack`() {
        // One unit of latitude_i is about a centimetre; only the identical positions precision rounding produces count.
        val nodes = listOf(node(1), node(2), node(3, longitudeI = SPOT_LONGITUDE_I + 1))

        val slots = MapNodeStacks.group(nodes, myNodeNum = null).slots

        assertEquals(setOf(1, 2), slots.keys)
        assertNull(slots[3])
    }

    @Test
    fun `this node then favorites then the most recently heard go on top`() {
        val nodes =
            listOf(
                node(1, lastHeard = 100),
                node(2, lastHeard = 300),
                node(3, lastHeard = 50, isFavorite = true),
                node(4, lastHeard = 10),
            )

        val slots = MapNodeStacks.group(nodes, myNodeNum = 4).slots

        assertEquals(listOf(4, 3, 2, 1), nodes.map { it.num }.sortedBy { slots.getValue(it).index })
    }

    @Test
    fun `node number breaks a tie so the order does not shuffle between refreshes`() {
        val slots = MapNodeStacks.group(listOf(node(9), node(2), node(5)), myNodeNum = null).slots

        assertEquals(listOf(2, 5, 9), slots.entries.sortedBy { it.value.index }.map { it.key })
    }

    @Test
    fun `the same node reaching the map twice counts once`() {
        val slots = MapNodeStacks.group(listOf(node(1), node(1), node(2)), myNodeNum = null).slots

        assertEquals(2, slots.getValue(1).count)
    }

    @Test
    fun `nodes without a fix are not stacked at the origin`() {
        val slots = MapNodeStacks.group(listOf(node(1, 0, 0), node(2, 0, 0)), myNodeNum = null).slots

        assertTrue(slots.isEmpty())
    }

    @Test
    fun `the top marker stays on the true position and the next three peek out in different directions`() {
        val offsets = (0 until MapNodeStacks.MAX_VISIBLE).map { MapNodeStacks.offsetDp(NodeStackSlot(it, count = 9)) }

        assertEquals(0f to 0f, offsets.first())
        assertTrue(offsets.drop(1).none { it == 0f to 0f })
        assertEquals(offsets.size, offsets.toSet().size)
    }

    @Test
    fun `the largest peek offset is what the marker padding has to cover`() {
        val offsets = (1 until MapNodeStacks.MAX_VISIBLE).map { MapNodeStacks.offsetDp(NodeStackSlot(it, count = 9)) }

        assertEquals(offsets.maxOf { (dx, dy) -> maxOf(abs(dx), abs(dy)) }, MapNodeStacks.MAX_PEEK_DP)
    }

    @Test
    fun `markers past the visible few hide under the top one`() {
        val hidden = (MapNodeStacks.MAX_VISIBLE until 9).map { MapNodeStacks.offsetDp(NodeStackSlot(it, count = 9)) }

        assertTrue(hidden.all { it == 0f to 0f })
    }

    @Test
    fun `only the top of a stack too big to show carries the count`() {
        assertFalse(NodeStackSlot(index = 0, count = MapNodeStacks.MAX_VISIBLE).showsCount)
        assertTrue(NodeStackSlot(index = 0, count = MapNodeStacks.MAX_VISIBLE + 1).showsCount)
        assertFalse(NodeStackSlot(index = 1, count = MapNodeStacks.MAX_VISIBLE + 1).showsCount)
    }

    @Test
    fun `the top of a stack draws above the rest without outranking a more prominent node`() {
        val ranks = (0..6).map { NodeStackSlot(it, count = 7).drawRank }

        assertEquals(ranks.sortedDescending(), ranks)
        assertTrue(ranks.first() > ranks[1])
        assertTrue(ranks.all { it >= 0f && it < 1f })
    }

    @Test
    fun `members lists the whole stack top first for every node in it`() {
        val nodes = listOf(node(1, lastHeard = 1), node(2, lastHeard = 2), node(3, latitudeI = SPOT_LATITUDE_I + 1))

        val members = MapNodeStacks.group(nodes, myNodeNum = null).members

        assertEquals(listOf(2, 1), members.getValue(1).map { it.num })
        assertEquals(members.getValue(1), members.getValue(2))
        assertNull(members[3])
    }

    private companion object {
        const val SPOT_LATITUDE_I = 407_100_000
        const val SPOT_LONGITUDE_I = -739_500_000
    }
}
