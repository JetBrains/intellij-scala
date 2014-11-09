package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

import scala.collection.mutable

/**
 * Pavel.Fatin, 09.05.2010
 */

class DepthFirstIterator(element: PsiElement, predicate: PsiElement => Boolean) extends Iterator[PsiElement] {
  private val stack = mutable.Stack[PsiElement](element)

  def hasNext = !stack.isEmpty

  def next() = {
    val element = stack.pop()
    if (predicate(element)) pushChildren(element)
    element
  }

  def pushChildren(element: PsiElement) {
      var child = element.getLastChild
      while (child != null) {
        stack.push(child)
        child = child.getPrevSibling
      }
  }
}