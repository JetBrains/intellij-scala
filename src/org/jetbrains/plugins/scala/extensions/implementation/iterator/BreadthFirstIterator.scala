package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement
import collection.immutable.Queue

/**
 * Pavel.Fatin, 09.05.2010
 */

class BreadthFirstIterator(element: PsiElement, predicate: PsiElement => Boolean) extends Iterator[PsiElement] {
  private var queue = Queue[PsiElement](element)
    
  def hasNext = !queue.isEmpty

  def next() = {
    val element = pop
    if (predicate(element)) pushChildren(element)    
    element
  }

  def pushChildren(element: PsiElement) {
      var child = element.getFirstChild
      while (child != null) {
        queue = queue.enqueue(child)
        child = child.getNextSibling
      }
  }
  
  def pop() = {
    val (element, tail) = queue.dequeue
    queue = tail
    element
  }
}