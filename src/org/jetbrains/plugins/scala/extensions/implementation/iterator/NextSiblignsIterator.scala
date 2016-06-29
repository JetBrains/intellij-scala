package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

/**
 * Pavel.Fatin, 11.05.2010
 */

class NextSiblignsIterator(element: PsiElement) extends Iterator[PsiElement] {
  private var current = element.getNextSibling

  def hasNext: Boolean = current != null

  def next(): PsiElement = {
    val result = current
    current = current.getNextSibling
    result
  }
}