package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

/**
 * Pavel.Fatin, 11.05.2010
 */

class ChildrenIterator(element: PsiElement) extends Iterator[PsiElement] {
  private var current = Option(element).map(_.getFirstChild).orNull

  def hasNext: Boolean = current != null

  def next(): PsiElement = {
    val result = current
    current = current.getNextSibling
    result
  }
}