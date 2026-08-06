package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.openapi.util.TextRange
import com.intellij.psi.{NavigatablePsiElement, PsiElement, PsiFile}

trait NavigablePsiElementWrapper[E <: NavigatablePsiElement] extends NavigatablePsiElement {
  val delegate: E

  override final def navigate(requestFocus: Boolean): Unit =
    delegate.navigate(requestFocus)

  override final def canNavigate: Boolean =
    delegate.canNavigate

  override final def canNavigateToSource: Boolean =
    delegate.canNavigateToSource

  override def getPrevSibling: PsiElement =
    delegate.getPrevSibling

  override def getNextSibling: PsiElement =
    delegate.getNextSibling

  override def getTextRange: TextRange =
    delegate.getTextRange

  override def getTextOffset: Int =
    delegate.getTextOffset

  override def getContainingFile: PsiFile =
    delegate.getContainingFile
}
