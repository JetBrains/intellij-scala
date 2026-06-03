package org.jetbrains.plugins.scala.conversion

import scala.collection.mutable

package object ast {

  private[conversion]
  def getChildren(node: IntermediateNode): Seq[IntermediateNode] =
    node match {
      case c: ExpressionsHolderNodeBase => c.children
      case _ => Nil
    }

  private[conversion]
  def breadthFirst(
    node: IntermediateNode,
    predicate: IntermediateNode => Boolean = _ => true
  ): Iterator[IntermediateNode] =
    new BreadthFirstIterator(node, predicate)

  private class BreadthFirstIterator(
    node: IntermediateNode,
    predicate: IntermediateNode => Boolean
  ) extends Iterator[IntermediateNode] {
    private val queue: mutable.Queue[IntermediateNode] =
      if (node != null) mutable.Queue(node)
      else mutable.Queue.empty

    override def hasNext: Boolean = queue.nonEmpty

    override def next(): IntermediateNode = {
      val element = queue.dequeue()
      if (predicate(element)) {
        pushChildren(element)
      }
      element
    }

    private def pushChildren(element: IntermediateNode): Unit = {
      element match {
        case composite: ExpressionsHolderNode =>
          queue.enqueueAll(composite.children)
        case _ =>
      }
    }
  }
}
