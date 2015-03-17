package org.jetbrains.plugins.scala.codeInspection.suppression

import com.intellij.codeInspection.{SuppressQuickFix, InspectionSuppressor}
import com.intellij.psi.PsiElement

/**
 * @author Nikolay.Tropin
 */
class ScalaInspectionSuppressor extends InspectionSuppressor {
  override def isSuppressedFor(element: PsiElement, toolId: String): Boolean = {
    ScalaSuppressableInspectionTool.findElementToolSuppressedIn(element, toolId).isDefined
  }

  override def getSuppressActions(element: PsiElement, toolShortName: String): Array[SuppressQuickFix] = {
    ScalaSuppressableInspectionTool.suppressActions(toolShortName)
  }
}
