package org.jetbrains.bsp.project.test.environment

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import org.jetbrains.bsp.project.test.environment.BspJvmEnvironment._
import org.jetbrains.bsp.{BspBundle, BspUtil, Icons}
import org.jetbrains.plugins.scala.worksheet.WorksheetFile
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.TopComponentAction
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

import javax.swing.Icon

class ConfigureBspTargetForWorksheet extends AnAction with TopComponentAction {
  override def genericText: String = BspBundle.message("bsp.task.choose.target.title")

  override def actionIcon: Icon = Icons.BSP_TARGET

  override protected def isActionEnabledForFile(file: WorksheetFile): Boolean = {
    val module = findModule(file)
    module.exists(BspUtil.isBspModule)
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
