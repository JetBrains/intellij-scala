package org.jetbrains.plugins.scala.codeInspection.packageNameInspection

import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.settings._

/**
 * @author Alexander Podkhalyuzin
 */

class EnablePerformanceProblemsQuickFix(project: Project) extends LocalQuickFix {

  val settings: ScalaProjectSettings = ScalaProjectSettings.getInstance(project)
  val ignoreSettings: Boolean = settings.isIgnorePerformance

  override def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    settings.setIgnorePerformance(!ignoreSettings)
  }

  private def enable: Boolean = !ignoreSettings

  override def getName: String =
    if (enable) InspectionBundle.message("enable.setting")
    else InspectionBundle.message("disable.setting")

  override def getFamilyName: String =
    if (enable) InspectionBundle.message("family.name.enable.setting")
    else InspectionBundle.message("fimaly.name.disable.setting")
}