package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

import scala.collection.mutable


/**
 * Pavel.Fatin, 09.05.2010
 */

class BreadthFirstIterator(element: PsiElement, predicate: PsiElement => Boolean) extends Iterator[PsiElement] {
  private val queue: mutable.Queue[PsiElement] =
    if (element != null) mutable.Queue(element)
    else mutable.Queue.empty

  def hasNext: Boolean = queue.nonEmpty

  def next(): PsiElement = {
    val element = queue.dequeue()
    if (predicate(element)) pushChildren(element)
    element
  }

  def pushChildren(element: PsiElement) {
      var child = element.getFirstChild
      while (child != null) {
        queue.enqueue(child)
        child = child.getNextSibling
      }
  }
}