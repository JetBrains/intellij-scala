package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

class ContextsIterator(element: PsiElement, strict: Boolean = true) extends Iterator[PsiElement] {
  private var current = if (strict && element != null) element.getContext else element

  override def hasNext: Boolean = current != null

  override def next(): PsiElement = {
    val result = current
    current = current.getContext
    result
  }
}