package org.jetbrains.bsp.codeInspection

import com.intellij.codeInspection.{InspectionSuppressor, SuppressQuickFix}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.bsp.codeInspection.BspScalaCliInspectionSuppressor.ScalaCliBspServerName
import org.jetbrains.bsp.project.BspExternalSystemUtil
import org.jetbrains.plugins.scala.codeInspection.packageNameInspection.ScalaPackageNameInspection
import org.jetbrains.plugins.scala.lang.psi.api.ScFile

final class BspScalaCliInspectionSuppressor extends InspectionSuppressor {

  private val suppressedToolIds: Set[String] = Set(
    ScalaPackageNameInspection.ToolId
  )

  override def isSuppressedFor(element: PsiElement, toolId: String): Boolean =
    element match {
      case scalaFile: ScFile =>
        suppressedToolIds.contains(toolId) && isBspScalaCliProject(scalaFile.getProject)
      case _ =>
        false
    }

  private def isBspScalaCliProject(project: Project): Boolean = {
    val projectData = BspExternalSystemUtil.getBspProjectData(project)
    projectData.exists(_.serverDisplayName == ScalaCliBspServerName)
  }

  override def getSuppressActions(element: PsiElement, toolId: String): Array[SuppressQuickFix] = Array()
}

object BspScalaCliInspectionSuppressor {
  private val ScalaCliBspServerName = "scala-cli"
}
