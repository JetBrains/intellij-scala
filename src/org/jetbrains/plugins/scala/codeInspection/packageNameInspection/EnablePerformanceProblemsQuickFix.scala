package org.jetbrains.plugins.scala.codeInspection.packageNameInspection

import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.settings._

/**
 * @author Alexander Podkhalyuzin
 */

class EnablePerformanceProblemsQuickFix(project: Project) extends LocalQuickFix {
  val settings = ScalaProjectSettings.getInstance(project)
  val ignoreSettings = settings.isIgnorePerformance

  def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    settings.setIgnorePerformance(!ignoreSettings)
  }

  val enable: String = if (!ignoreSettings) "Enable" else "Disable"
  def getName: String =
    enable + " setting, solving resolve problems " + (
                    if (!ignoreSettings) "(this can cause editor performance problems"
                    else "(this can improve editor performance"
                    ) + ")."

  def getFamilyName: String =  enable + " setting"
}