package org.jetbrains.plugins.scala.codeInsight.navigation

import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer
import com.intellij.ide.util.PsiElementRenderingInfo
import com.intellij.psi.PsiElement

import javax.swing.Icon

//noinspection ReferencePassedToNls
class DelegatingPsiTargetPresentationRenderer[T <: PsiElement](private val renderingInfo: PsiElementRenderingInfo[T]) extends PsiTargetPresentationRenderer[T] {
  override def getElementText(element: T): String = renderingInfo.getPresentableText(element)

  override def getContainerText(element: T): String = renderingInfo.getContainerText(element)

  override def getIcon(element: T): Icon = renderingInfo.getIcon(element)
}
