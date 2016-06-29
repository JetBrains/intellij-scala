package org.jetbrains.plugins.scala
package extensions.implementation.iterator

import com.intellij.psi.PsiElement

/**
 * Pavel.Fatin, 11.05.2010
 */

class PrevElementsIterator(element: PsiElement) extends Iterator[PsiElement] {
  private var current = element

  def hasNext: Boolean = current != null

  def next(): PsiElement = {
    val result = current
    current = current.getPrevSibling
    result
  }
}