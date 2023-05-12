package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting.Alignment
import com.intellij.lang.ASTNode

private[formatting]
class SubBlocksContext(
  val additionalNodes: Seq[ASTNode] = Seq(),
  val alignment: Option[Alignment] = None,
  val childrenAdditionalContexts: Map[ASTNode, SubBlocksContext] = Map()
) {
  def lastNode(firstNode: ASTNode): ASTNode =
    lastNode.filter(_ != firstNode).orNull

  private def lastNode: Option[ASTNode] = {
    val nodes1 = childrenAdditionalContexts.map { case (_, context) => context.lastNode }.collect { case Some(x) => x }
    val nodes2 = childrenAdditionalContexts.map { case (child, _) => child }
    val nodes = nodes1 ++ nodes2 ++ additionalNodes
    if (nodes.nonEmpty) {
      Some(nodes.maxBy(_.getTextRange.getEndOffset))
    } else {
      None
    }
  }
}

private[formatting]
object SubBlocksContext {
  def withChild(
    childNode: ASTNode,
    childAdditionalNodes: Seq[ASTNode],
    childAlignment: Option[Alignment] = None,
    childChildrenAdditionalContexts: Map[ASTNode, SubBlocksContext] = Map()
  ): SubBlocksContext = {
    val childContext = new SubBlocksContext(childAdditionalNodes, childAlignment, childChildrenAdditionalContexts)
    val childrenAdditionalContexts = Map(childNode -> childContext)
    new SubBlocksContext(
      additionalNodes = Seq(),
      alignment = None,
      childrenAdditionalContexts
    )
  }

  def withChildrenAlignments(childNodesAlignment: Map[ASTNode, Alignment]): SubBlocksContext = {
    val childrenAdditionalContexts = childNodesAlignment
      .view
      .mapValues(alignment => new SubBlocksContext(Seq(), Some(alignment), Map()))
      .toMap
    new SubBlocksContext(
      additionalNodes = Seq(),
      alignment = None,
      childrenAdditionalContexts
    )
  }
}