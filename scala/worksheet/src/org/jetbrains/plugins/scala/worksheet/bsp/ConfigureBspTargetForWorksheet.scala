package org.jetbrains.plugins.scala.worksheet.bsp

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import com.intellij.util.SlowOperations
import org.jetbrains.bsp.project.test.environment.BspJvmEnvironment.promptUserToSelectBspTargetForWorksheet
import org.jetbrains.bsp.{BspUtil, Icons}
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.TopComponentAction
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings
import org.jetbrains.plugins.scala.worksheet.{WorksheetBundle, WorksheetFile}

import javax.swing.Icon
import scala.util.Using

private final class ConfigureBspTargetForWorksheet extends AnAction with TopComponentAction {
  override def genericText: String = WorksheetBundle.message("worksheet.configuration.choose.bsp.target")

  override def actionIcon: Icon = Icons.BSP_TARGET

  override protected def isActionEnabledForFile(file: WorksheetFile): Boolean = {
    Using.resource(SlowOperations.knownIssue("SCL-22095, SCL-22097")) { _ =>
      val module = findModule(file)
      module.exists(BspUtil.isBspModule)
    }
  }

  override def actionPerformed(e: AnActionEvent): Unit = {
    for {
      (_, file) <- getCurrentScalaWorksheetEditorAndFile(e)
      module = findModule(file)
    } {
      module.foreach(promptUserToSelectBspTargetForWorksheet)
    }
  }

  private def findModule(file: PsiFile): Option[Module] =
    WorksheetFileSettings(file).getModule

  override def update(e: AnActionEvent): Unit = {
    updatePresentationEnabled(e)

    val isVisible = isInBspProject(e)
    this.setVisible(isVisible)
    this.setEnabled(isVisible)
  }

  private def isInBspProject(e: AnActionEvent): Boolean =
    Option(e.getProject).exists(BspUtil.isBspProject)
}
