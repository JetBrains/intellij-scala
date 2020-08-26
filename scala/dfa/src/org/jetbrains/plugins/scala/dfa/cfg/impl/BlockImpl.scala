package org.jetbrains.plugins.scala.dfa
package cfg
package impl

private final class BlockImpl[Info](override val blockIndex: Int, override val nodeBegin: Int) extends Block {
  override type SourceInfo = Info

  var _graph: Graph[Info] = _
  var _endIndex: Int = -1

  override def graph: Graph[Info] = _graph.ensuring(_ != null)

  override def nodeEnd: Int = _endIndex.ensuring(_ >= 0)

  override def nodes: Seq[Node] = graph.nodes.view(nodeBegin, nodeEnd)

  override def nodeIndices: Range = nodeBegin until nodeEnd
}
