package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

class PrevSiblignsIterator(element: PsiElement) extends Iterator[PsiElement] {

  private var current: PsiElement = if (element == null) null else element.getPrevSibling

  override def hasNext: Boolean = current != null

  override def next(): PsiElement = {
    val result = current
    current = current.getPrevSibling
    result
  }
}