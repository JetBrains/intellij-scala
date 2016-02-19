package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

/**
  * Created by kate on 17.12.15.
  */
class ParentsWithSelfIterator(element: PsiElement) extends Iterator[PsiElement] {
  private var current = element

  def hasNext = current != null

  def next() = {
    val result = current
    current = current.getParent
    result
  }
}
