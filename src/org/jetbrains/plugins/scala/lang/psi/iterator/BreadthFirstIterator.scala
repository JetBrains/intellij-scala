package org.jetbrains.plugins.scala.lang.psi.iterator

import com.intellij.psi.PsiElement
import collection.immutable.Queue

/**
 * Pavel.Fatin, 09.05.2010
 */

/**
 * Breadth-first state machine tree-traversal iterator implementation, 
 * faster than stack-based implementation, up to 2x faster than ScalaRecursiveElementVisitor.
 */
class BreadthFirstIterator(element: PsiElement) extends Iterator[PsiElement] {
  var current = element
  var continuations = Queue[PsiElement]()

  def hasNext = current != null

  def next() = {
    val result = current
    current = nextFor(current)
    result
  }

  def nextFor(e: PsiElement): PsiElement = {
    if (e.eq(element)) return e.getFirstChild

    val sibling = e.getNextSibling
    val child = e.getFirstChild

    if (child != null) continuations = continuations.enqueue(child)

    if (sibling != null) return sibling

    if (continuations.isEmpty) return null

    val (result, tail) = continuations.dequeue
    continuations = tail
    result
  }
}