package org.jetbrains.plugins.scala.editor.importOptimizer

import com.intellij.lang.ASTNode

import scala.annotation.tailrec
import scala.collection.mutable
import scala.reflect.ClassTag

trait BufferOperations[Buffer, Elem] {
  def remove(buffer: Buffer, startIdx: Int, count: Int): Unit

  def insert(buffer: Buffer, idx: Int, toInsert: Seq[Elem]): Unit

  def asArray(buffer: Buffer): Array[Elem]
}

object BufferOperations {

  /**
   * In production code, [[astNodeChildrenOperations]] is the only [[BufferOperations]] implementation used.
   * Currently [[ScalaImportOptimizer]] is its only place of usage.
   */
  private[importOptimizer] implicit val astNodeChildrenOperations: BufferOperations[AstChildrenBuffer, ASTNode] =
    new BufferOperations[AstChildrenBuffer, ASTNode] {

      override def remove(buffer: AstChildrenBuffer, startIdx: Int, count: Int): Unit =
        buffer.remove(startIdx, count)

      override def insert(buffer: AstChildrenBuffer, idx: Int, toInsert: Seq[ASTNode]): Unit =
        buffer.insert(idx, toInsert)

      override def asArray(buffer: AstChildrenBuffer): Array[ASTNode] =
        buffer.asArray
    }

  /**
   * [[scalaBufferOperations]] is only used in org.jetbrains.plugins.scala.lang.optimize.IncrementalBufferUpdateTest.
   */
  implicit def scalaBufferOperations[E: ClassTag, B <: mutable.Buffer[E]]: BufferOperations[B, E] =
    new BufferOperations[B, E] {
      override def remove(buffer: B, startIdx: Int, count: Int): Unit = buffer.remove(startIdx, count)

      override def insert(buffer: B, idx: Int, toInsert: Seq[E]): Unit = buffer.insertAll(idx, toInsert)

      override def asArray(buffer: B): Array[E] = buffer.toArray
    }
}

private case class AstChildrenBuffer(parent: ASTNode,
                                     var firstElemIndex: Int,
                                     var lastElemIndex: Int) {

  final def get(idx: Int): ASTNode = {
    repeatOn(parent.getFirstChildNode, firstElemIndex + idx)(_.getTreeNext)
  }

  @tailrec
  private def repeatOn[T](first: T, n: Int)(action: T => T): T = {
    if (n == 0) first
    else repeatOn(action(first), n - 1)(action)
  }

  def remove(startIdx: Int, count: Int): Unit = {
    val firstToRemove = get(startIdx)
    val firstToKeep = get(startIdx + count)
    parent.removeRange(firstToRemove, firstToKeep)

    lastElemIndex -= count
  }

  def insert(idx: Int, toInsert: Seq[ASTNode]): Unit = {
    val anchorBefore = get(idx)
    parent.addChildren(toInsert.head, toInsert.last.getTreeNext, anchorBefore)

    lastElemIndex += toInsert.size
  }

  def asArray: Array[ASTNode] = {
    val allChildren = parent.getChildren(null)
    allChildren.slice(firstElemIndex, lastElemIndex + 1)
  }
}

private object AstChildrenBuffer {
  def apply(parent: ASTNode, firstChild: ASTNode, lastChild: ASTNode): AstChildrenBuffer = {
    val children = parent.getChildren(null)
    val firstIdx = children.indexOf(firstChild)
    val lastIdx = children.indexOf(lastChild)
    AstChildrenBuffer(parent, firstIdx, lastIdx)
  }
}