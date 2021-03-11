package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

/**
 * Pavel.Fatin, 09.05.2010
 */

final class DepthFirstIterator(element: PsiElement, predicate: PsiElement => Boolean) extends Iterator[PsiElement] {
  private var stack: List[PsiElement] =
    if (element == null)  List.empty
    else                  List(element)

  override def hasNext: Boolean = stack.nonEmpty

  override def next(): PsiElement = {
    val element = stack.head
    stack = stack.tail
    if (predicate(element)) pushChildren(element)
    element
  }

  private def pushChildren(element: PsiElement): Unit = {
      var child = element.getLastChild
      while (child != null) {
        stack = child +: stack
        child = child.getPrevSibling
      }
  }
}