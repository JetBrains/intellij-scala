package org.jetbrains.plugins.scala
package extensions.implementation.iterator

import com.intellij.psi.PsiElement

/**
 * Pavel.Fatin, 11.05.2010
 */

class PrevElementsIterator(element: PsiElement) extends Iterator[PsiElement] {
  private var current = element

  def hasNext = current != null

  def next() = {
    val result = current
    current = current.getPrevSibling
    result
  }
}