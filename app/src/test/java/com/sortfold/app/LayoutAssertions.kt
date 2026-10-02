package com.sortfold.app

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert

/**
 * Layout-contract assertions for the whole app. Used by the matrix tests:
 * every interactive element must (a) not overlap a sibling interactive
 * element, (b) stay inside the window bounds, so nothing is ever clipped or
 * stacked regardless of width, font scale, locale or navigation mode.
 */
object LayoutAssertions {

    private fun interactiveChildren(node: SemanticsNode): List<androidx.compose.ui.semantics.SemanticsNode> =
        buildList {
            val clickable = node.config.getOrNull(SemanticsActions.OnClick)
            if (clickable != null) add(node)
            node.children.forEach { addAll(interactiveChildren(it)) }
        }

    private fun collectWithParents(
        node: SemanticsNode,
        parent: SemanticsNode?,
        out: MutableList<Pair<SemanticsNode, SemanticsNode?>>,
    ) {
        val clickable = node.config.getOrNull(SemanticsActions.OnClick)
        if (clickable != null) out += node to parent
        node.children.forEach { collectWithParents(it, node, out) }
    }

    /**
     * Walks the semantics tree and asserts the layout contract.
     * [windowBounds] is the content area the app may draw into.
     */
    fun assertLayoutContract(interaction: SemanticsNodeInteraction, windowBounds: Rect) {
        val root = interaction.fetchSemanticsNode()
        val interactives = mutableListOf<Pair<SemanticsNode, SemanticsNode?>>()
        collectWithParents(root, null, interactives)

        val problems = mutableListOf<String>()

        // (a) Nothing leaves the window bounds.
        interactives.forEach { (node, _) ->
            val b = node.boundsInRoot
            if (b.left < windowBounds.left - 0.5f || b.right > windowBounds.right + 0.5f ||
                b.top < windowBounds.top - 0.5f || b.bottom > windowBounds.bottom + 0.5f
            ) {
                problems += "outside window: ${describe(node)} bounds=$b"
            }
        }

        // (b) No two sibling interactive nodes overlap.
        val byParent = interactives.filter { it.second != null }.groupBy { it.second!! }
        byParent.forEach { (parent, children) ->
            for (i in children.indices) {
                for (j in i + 1 until children.size) {
                    val a = children[i].first.boundsInRoot
                    val b = children[j].first.boundsInRoot
                    val overlap = Rect(
                        maxOf(a.left, b.left), maxOf(a.top, b.top),
                        minOf(a.right, b.right), minOf(a.bottom, b.bottom),
                    )
                    val overlaps = overlap.width > 1f && overlap.height > 1f
                    if (overlaps) {
                        problems += "sibling overlap under ${describe(parent)}: " +
                            "${describe(children[i].first)} $a vs ${describe(children[j].first)} $b"
                    }
                }
            }
        }

        org.junit.Assert.assertTrue(
            "layout contract violations:\n" + problems.joinToString("\n"),
            problems.isEmpty(),
        )
    }

    private fun describe(node: SemanticsNode): String {
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("|") { it.text }
        val role = node.config.getOrNull(SemanticsProperties.Role)?.toString()?.substringAfterLast('.')
        return (role ?: "node") + (text?.let { " \"$it\"" } ?: "")
    }
}
