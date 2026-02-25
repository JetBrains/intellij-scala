package org.jetbrains.plugins.scala.codeInspection.suppression

import com.intellij.codeInspection.{InspectionSuppressor, SuppressQuickFix}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.incremental.Highlighting._

class ScalaInspectionSuppressor extends InspectionSuppressor {
  override def isSuppressedFor(element: PsiElement, toolId: String): Boolean = {
    val file = element.getContainingFile
    val project = if (file != null) file.getProject else element.getProject // Avoid tree walk-up

    if (!element.isVisible(project, file)) return false

    ScalaSuppressableInspectionTool.findElementToolSuppressedIn(element, toolId).isDefined
  }

  override def getSuppressActions(element: PsiElement, toolShortName: String): Array[SuppressQuickFix] = {
    val file = element.getContainingFile
    val project = if (file != null) file.getProject else element.getProject // Avoid tree walk-up

    if (!element.isVisible(project, file)) return Array.empty

    ScalaSuppressableInspectionTool.suppressActions(toolShortName)
  }
}
