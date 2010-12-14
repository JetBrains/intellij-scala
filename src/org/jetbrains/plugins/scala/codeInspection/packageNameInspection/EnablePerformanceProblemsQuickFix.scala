package org.jetbrains.plugins.scala.codeInspection.packageNameInspection

import com.intellij.openapi.project.Project
import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import java.lang.String

/**
 * @author Alexander Podkhalyuzin
 */

class EnablePerformanceProblemsQuickFix(project: Project) extends LocalQuickFix {
  val settings = ScalaPsiUtil.getSettings(project)
  val ignoreSettings = settings.IGNORE_PERFORMANCE_TO_FIND_ALL_CLASS_NAMES

  def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    settings.IGNORE_PERFORMANCE_TO_FIND_ALL_CLASS_NAMES = !ignoreSettings
  }

  val enable: String = if (!ignoreSettings) "Enable" else "Disable"
  def getName: String =
    enable + " setting, solving resolve problems" + (
                    if (!ignoreSettings) "(this can cause editor performance problems"
                    else "(this can improve editor performance"
                    ) + ")."

  def getFamilyName: String =  (enable) + " setting"
}