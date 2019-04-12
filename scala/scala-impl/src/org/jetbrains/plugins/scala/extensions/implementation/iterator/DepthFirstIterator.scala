package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

import scala.collection.mutable

/**
 * Pavel.Fatin, 09.05.2010
 */

class DepthFirstIterator(element: PsiElement, predicate: PsiElement => Boolean) extends Iterator[PsiElement] {
  private var stack: List[PsiElement] =
    if (element == null)  List.empty
    else                  List(element)

  def hasNext: Boolean = stack.nonEmpty

  def next(): PsiElement = {
    val element = stack.head
    stack = stack.tail
    if (predicate(element)) pushChildren(element)
    element
  }

  def pushChildren(element: PsiElement) {
      var child = element.getLastChild
      while (child != null) {
        stack = child +: stack
        child = child.getPrevSibling
      }
  }
}