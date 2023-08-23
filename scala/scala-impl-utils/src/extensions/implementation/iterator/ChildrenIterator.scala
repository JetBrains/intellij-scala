package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

class ChildrenIterator(element: PsiElement) extends Iterator[PsiElement] {

  private var current: PsiElement = if (element != null) element.getFirstChild else null

  override def hasNext: Boolean = current != null

  override def next(): PsiElement = {
    val result = current
    current = current.getNextSibling
    result
  }
}