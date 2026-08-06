package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

final class DepthFirstIterator(element: PsiElement, shouldProcessChildren: PsiElement => Boolean) extends Iterator[PsiElement] {
  private var stack: List[PsiElement] =
    if (element == null)  List.empty
    else                  List(element)

  override def hasNext: Boolean = stack.nonEmpty

  override def next(): PsiElement = {
    val element = stack.head
    stack = stack.tail
    if (shouldProcessChildren(element)) {
      pushChildren(element)
    }
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