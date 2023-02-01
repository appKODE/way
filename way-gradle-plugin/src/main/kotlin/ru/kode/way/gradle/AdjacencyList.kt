package ru.kode.way.gradle

internal typealias AdjacencyList = Map<Node, List<Node>>

internal inline fun AdjacencyList.forEachFlow(action: (Node.Flow, List<Node>) -> Unit) {
  this.forEach { (node, adjacent) ->
    if (node is Node.Flow) {
      action(node, adjacent)
    }
  }
}

internal inline fun <T : Any> AdjacencyList.mapFlow(action: (Node.Flow, List<Node>) -> T): List<T> {
  val out = mutableListOf<T>()
  this.forEach { (node, adjacent) ->
    if (node is Node.Flow) {
      out.add(action(node, adjacent))
    }
  }
  return out
}

internal fun AdjacencyList.findRootNode(): Node {
  keys.forEach {
    if (findParent(it) == null) return it
  }
  error("internal error: no root node in graph")
}

internal fun AdjacencyList.findParent(node: Node): Node? {
  entries.forEach { (n, adjacent) ->
    if (n == node) return@forEach // continue
    if (adjacent.contains(node)) {
      return n
    }
  }
  return null
}

/**
 * Finds all parents and returns them in the closest-to-farthest order, i.e. for
 * app -> screen1 -> screen2 -> screen3
 *
 * findAllParents(screen3) => [screen2, screen1, app]
 */
internal fun AdjacencyList.findAllParents(node: Node, includeThis: Boolean = false): List<Node> {
  val parents = ArrayList<Node>(this.size)
  if (includeThis) {
    parents.add(node)
  }
  var next: Node? = findParent(node)
  while (next != null) {
    parents.add(next)
    next = findParent(next)
  }
  return parents
}

internal fun AdjacencyList.findParentFlow(node: Node): Node? {
  var next: Node? = findParent(node)
  while (next != null) {
    if (next is Node.Flow) return next
    next = findParent(next)
  }
  return null
}

internal fun dfs(adjacencyList: AdjacencyList, root: Node, action: (Node) -> Unit) {
  val discovered = ArrayList<Node>(adjacencyList.size)
  val stack = ArrayDeque<Node>(adjacencyList.size)
  stack.add(root)
  while (stack.isNotEmpty()) {
    val v = stack.removeLast()
    if (!discovered.contains(v)) {
      action(v)
      discovered.add(v)
      stack.addAll(adjacencyList[v].orEmpty())
    }
  }
}
