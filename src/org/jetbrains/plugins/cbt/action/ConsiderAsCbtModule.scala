package org.jetbrains.plugins.cbt.action

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.psi.PsiDirectory
import org.jetbrains.plugins.cbt.CBT
import org.jetbrains.plugins.cbt.Helpers._
import org.jetbrains.plugins.cbt.project.settings.CbtProjectSettings

class ConsiderAsCbtModule extends AnAction {
  override def update(e: AnActionEvent): Unit = {
    val presentation = e.getPresentation

    def enable() {
      presentation.setEnabled(true)
      presentation.setVisible(true)
    }

    def disable() {
      presentation.setEnabled(false)
      presentation.setVisible(false)
    }

    val dataContext = e.getDataContext
    val project = CommonDataKeys.PROJECT.getData(dataContext)

    if (!CBT.isCbtProject(project)) {
      disable()
    } else {
      try {
        val target = CommonDataKeys.PSI_ELEMENT.getData(dataContext)
        target match {
          case dir: PsiDirectory if CBT.isCbtModuleDir(dir.getVirtualFile) => enable()
          case _ => disable()
        }
      }
      catch {
        case _: Exception => disable()
      }
    }
  }

  override def actionPerformed(e: AnActionEvent): Unit = {
    val dataContext = e.getDataContext
    val moduleDir = CommonDataKeys.PSI_ELEMENT.getData(dataContext).asInstanceOf[PsiDirectory]
    val modulePath = moduleDir.getVirtualFile.getPath.toFile
    val project = CommonDataKeys.PROJECT.getData(dataContext)
    val projectSettings = CbtProjectSettings.getInstance(project, project.getBasePath)
    projectSettings.extraModules = (projectSettings.extraModules :+ modulePath).distinct
    println(s"""Extra project Modules: ${projectSettings.extraModules.map(_.getPath).mkString(",")}""")
  }
}