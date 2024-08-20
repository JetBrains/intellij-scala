package org.jetbrains.scalaCli.codeInspection

import com.intellij.codeInspection.{InspectionSuppressor, SuppressQuickFix}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.packageNameInspection.ScalaPackageNameInspection
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.scalaCli.bsp.ScalaCliBspUtils

final class BspScalaCliInspectionSuppressor extends InspectionSuppressor {

  private val suppressedToolIds: Set[String] = Set(
    ScalaPackageNameInspection.ToolId
  )

  override def isSuppressedFor(element: PsiElement, toolId: String): Boolean =
    element match {
      case scalaFile: ScFile =>
        suppressedToolIds.contains(toolId) && ScalaCliBspUtils.isBspScalaCliProject(scalaFile.getProject)
      case _ =>
        false
    }

  override def getSuppressActions(element: PsiElement, toolId: String): Array[SuppressQuickFix] = Array()
}