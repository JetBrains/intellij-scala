package org.jetbrains.plugins.scala.codeInsight.navigation

import com.intellij.codeInsight.navigation.GotoTargetPresentationProvider
import com.intellij.ide.util.{PsiClassRenderingInfo, PsiElementRenderingInfo}
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiPresentationUtils.{extensionMethodContainerText, extensionMethodPresentableText}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScExportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

//noinspection ApiStatus,UnstableApiUsage
final class ScalaGotoTargetPresentationProvider extends GotoTargetPresentationProvider {

  override def getTargetPresentation(element: PsiElement, differentNames: Boolean): TargetPresentation = element match {
    case exportStmt: ScExportStmt =>
      val containingType = Option(PsiTreeUtil.getParentOfType(exportStmt, classOf[ScTypeDefinition]))
      val presentation = containingType.map(typeDefinitionPresentation)
      presentation.orNull
    case function: ScFunction if function.isExtensionMethod =>
      extensionMethodPresentation(function)
    case _ =>
      null
  }

  private def typeDefinitionPresentation(containingType: ScTypeDefinition): TargetPresentation =
    PsiElementRenderingInfo.targetPresentation(containingType, PsiClassRenderingInfo.INSTANCE)

  private def extensionMethodPresentation(function: ScFunction): TargetPresentation =
    PsiElementRenderingInfo.targetPresentation(function, new PsiElementRenderingInfo[ScFunction] {
      override def getPresentableText(element: ScFunction): String =
        extensionMethodPresentableText(element)

      override def getContainerText(element: ScFunction): String =
        extensionMethodContainerText(element).orNull
    })
}
