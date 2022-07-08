package org.jetbrains.plugins.scala.codeInspection.suppression

import com.intellij.codeInspection.{InspectionSuppressor, SuppressQuickFix}
import com.intellij.psi.PsiElement

class ScalaInspectionSuppressor extends InspectionSuppressor {
  override def isSuppressedFor(element: PsiElement, toolId: String): Boolean = {
    ScalaSuppressableInspectionTool.findElementToolSuppressedIn(element, toolId).isDefined
  }

  override def getSuppressActions(element: PsiElement, toolShortName: String): Array[SuppressQuickFix] = {
    ScalaSuppressableInspectionTool.suppressActions(toolShortName)
  }
}
