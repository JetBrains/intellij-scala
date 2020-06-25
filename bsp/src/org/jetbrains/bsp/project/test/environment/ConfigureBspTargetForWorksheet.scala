package org.jetbrains.bsp.project.test.environment

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import javax.swing.Icon
import org.jetbrains.bsp.BspBundle
import org.jetbrains.bsp.BspUtil
import org.jetbrains.bsp.Icons
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

  override def actionPerformed(e: AnActionEvent): Unit = {
    findFile(e).map(findModule).foreach(promptUserToSelectBspTargetForWorksheet)
  }

  private def findFile(e: AnActionEvent): Option[PsiFile] = {
    ScalaActionUtil.getFileFrom(e).orElse(getSelectedFile(e.getProject))
  }

  private def findModule(file: PsiFile): Module = {
    WorksheetFileSettings(file).getModuleFor
  }

  override def updateInner(e: AnActionEvent): Unit = {
    Option(e).flatMap(e => Option(e.getProject)).foreach { project =>
      if (BspUtil.isBspProject(project)) {
        setVisible(true)
        super.updateInner(e)
      } else {
        setVisible(false)
      }
    }
  }

}
