package org.jetbrains.plugins.scala.dfa
package cfg


class Graph[+Info](final val nodes: IndexedSeq[Node]) {
  final def apply(index: Int): Node = nodes(index)

  final lazy val hasIncomingJump: Set[Node] =
    nodes.collect { case jump: Jumping => jump.target }.toSet

  final def asmText(showIndices: Boolean = false): String = {
    val builder = new StringBuilder
    for (node <- nodes) {
      builder ++= node.asmString(showIndex = showIndices, showLabel = hasIncomingJump(node))
      builder += '\n'
    }

    builder.result()
  }
}
