package hey.skipad.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * A lightweight, immutable description of a single accessibility node.
 * The detector only ever reads these snapshots so the decision logic stays
 * pure and testable, while [NodeTree.refs] keeps the live nodes for clicking.
 */
data class NodeSnapshot(
    val index: Int,
    val parentIndex: Int,
    val className: String,
    val viewId: String,
    val text: String,
    val contentDescription: String,
    val bounds: Rect,
    val clickable: Boolean,
    val enabled: Boolean,
    val visible: Boolean,
    val packageName: String,
    val childCount: Int,
    val depth: Int,
)

data class NodeTree(
    val nodes: List<NodeSnapshot>,
    val refs: List<AccessibilityNodeInfo>,
)

object NodeExtractor {

    private const val MAX_NODES = 800
    private const val MAX_CHILDREN = 80
    private const val MAX_DEPTH = 60

    fun extract(root: AccessibilityNodeInfo): NodeTree {
        val nodes = ArrayList<NodeSnapshot>(96)
        val refs = ArrayList<AccessibilityNodeInfo>(96)
        val queue = ArrayDeque<Entry>()
        queue.addLast(Entry(root, -1, 0))

        while (queue.isNotEmpty() && nodes.size < MAX_NODES) {
            val entry = queue.removeFirst()
            val node = entry.node
            val index = nodes.size
            refs.add(node)
            nodes.add(snapshotOf(node, index, entry.parentIndex, entry.depth))

            if (entry.depth >= MAX_DEPTH) continue
            val childCount = minOf(node.childCount, MAX_CHILDREN)
            for (i in 0 until childCount) {
                val child = node.getChild(i) ?: continue
                queue.addLast(Entry(child, index, entry.depth + 1))
            }
        }
        return NodeTree(nodes, refs)
    }

    private fun snapshotOf(
        node: AccessibilityNodeInfo,
        index: Int,
        parentIndex: Int,
        depth: Int,
    ): NodeSnapshot {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return NodeSnapshot(
            index = index,
            parentIndex = parentIndex,
            className = node.className?.toString().orEmpty(),
            viewId = node.viewIdResourceName.orEmpty(),
            text = node.text?.toString().orEmpty(),
            contentDescription = node.contentDescription?.toString().orEmpty(),
            bounds = rect,
            clickable = node.isClickable,
            enabled = node.isEnabled,
            visible = node.isVisibleToUser,
            packageName = node.packageName?.toString().orEmpty(),
            childCount = node.childCount,
            depth = depth,
        )
    }

    private class Entry(
        val node: AccessibilityNodeInfo,
        val parentIndex: Int,
        val depth: Int,
    )
}
