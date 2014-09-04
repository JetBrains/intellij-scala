package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

import scala.collection.mutable


/**
 * Pavel.Fatin, 09.05.2010
 */

class BreadthFirstIterator(element: PsiElement, predicate: PsiElement => Boolean) extends Iterator[PsiElement] {
  private val queue = mutable.Queue[PsiElement](element)

  def hasNext = !queue.isEmpty

  def next() = {
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