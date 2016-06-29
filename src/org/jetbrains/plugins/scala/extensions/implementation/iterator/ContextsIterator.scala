package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

/**
 * Pavel.Fatin, 11.05.2010
 */

class ContextsIterator(element: PsiElement) extends Iterator[PsiElement] {
  private var current = element.getContext

  def hasNext: Boolean = current != null

  def next(): PsiElement = {
    val result = current
    current = current.getContext
    result
  }
}