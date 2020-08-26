package org.jetbrains.plugins.scala.dfa.cfg

trait Block {
  type SourceInfo

  def graph: Graph[SourceInfo]

  def blockIndex: Int

  def nodeBegin: Int
  def nodeEnd: Int

  def nodes: Seq[Node]
  def nodeIndices: Range

  final def headNode: Option[Node] = nodes.headOption
  final def lastNode: Option[Node] = nodes.lastOption
}
