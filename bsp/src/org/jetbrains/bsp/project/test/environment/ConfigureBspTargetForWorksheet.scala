package org.jetbrains.bsp.project.test.environment

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import javax.swing.Icon
import org.jetbrains.bsp.{BspBundle, BspUtil, Icons}
import org.jetbrains.bsp.project.test.environment.BspJvmEnvironment._
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.TopComponentAction
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

class ConfigureBspTargetForWorksheet extends AnAction with TopComponentAction {
  override def genericText: String = BspBundle.message("bsp.task.choose.target.title")

  override def actionIcon: Icon = Icons.BSP_TARGET

  override def acceptFile(file: ScalaFile): Boolean = {
    val module = findModule(file)
    BspUtil.isBspModule(module)
  }

  override def actionPerformed(e: AnActionEvent): Unit =
    findFile(e).map(findModule).foreach(promptUserToSelectBspTargetForWorksheet)

  private def findFile(e: AnActionEvent): Option[PsiFile] =
    ScalaActionUtil.getFileFrom(e).orElse(getSelectedFile(e))

  private def findModule(file: PsiFile): Module =
    WorksheetFileSettings(file).getModule.getOrElse {
      throw new AssertionError(s"Module of worksheet is empty: `${file.getVirtualFile}`")
    }

  override def updateInner(e: AnActionEvent): Unit = {
    super.updateInner(e)
    val isVisible = isInBspProject(e)
    this.setVisible(isVisible)
  }

  private def isInBspProject(e: AnActionEvent): Boolean =
    Option(e.getProject).exists(BspUtil.isBspProject)
}
