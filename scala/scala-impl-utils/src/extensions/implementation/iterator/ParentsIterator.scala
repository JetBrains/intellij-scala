package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

class ParentsIterator(element: PsiElement, strict: Boolean = true) extends Iterator[PsiElement] {
  private var current = if (strict && element != null) element.getParent else element

  override def hasNext: Boolean = current != null

  override def next(): PsiElement = {
    val result = current
    current = current.getParent
    result
  }
}