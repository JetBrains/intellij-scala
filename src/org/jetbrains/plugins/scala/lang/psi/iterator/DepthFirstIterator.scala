package org.jetbrains.plugins.scala.lang.psi.iterator

import com.intellij.psi.PsiElement

/**
 * Pavel.Fatin, 09.05.2010
 */

class DepthFirstIterator(element: PsiElement, predicate: PsiElement => Boolean) extends Iterator[PsiElement] {
  private var stack = List[PsiElement](element)

  def hasNext = !stack.isEmpty

  def next() = {
    val element = pop
    if (predicate(element)) pushChildren(element)
    element
  }

  def pushChildren(element: PsiElement) {
      var child = element.getLastChild
      while (child != null) {
        stack = child :: stack
        child = child.getPrevSibling
      }
  }
  
  def pop() = {
    val element = stack.head
    stack = stack.tail
    element
  }
}