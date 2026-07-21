package org.jetbrains.plugins.scala.codeInsight.navigation

import com.intellij.codeInsight.navigation.GotoTargetPresentationProvider
import com.intellij.ide.util.{PsiClassRenderingInfo, PsiElementRenderingInfo}
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScExportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

//noinspection ApiStatus,UnstableApiUsage
final class ScalaGotoTargetPresentationProvider extends GotoTargetPresentationProvider {

  override def getTargetPresentation(element: PsiElement, differentNames: Boolean): TargetPresentation = element match {
    case exportStmt: ScExportStmt =>
      val containingType = Option(PsiTreeUtil.getParentOfType(exportStmt, classOf[ScTypeDefinition]))
      val presentation = containingType.map(typeDefinitionPresentation)
      presentation.orNull
    case _ =>
      null
  }

  private def typeDefinitionPresentation(containingType: ScTypeDefinition): TargetPresentation =
    PsiElementRenderingInfo.targetPresentation(containingType, PsiClassRenderingInfo.INSTANCE)
}
