package org.jetbrains.plugins.cbt.action

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.psi.PsiDirectory
import org.jetbrains.plugins.cbt.Helpers._
import org.jetbrains.plugins.cbt.project.settings.CbtProjectSettings

class ConsiderAsCbtModule extends AnAction {
  override def actionPerformed(e: AnActionEvent): Unit = {
    val dataContext = e.getDataContext
    val moduleDir = CommonDataKeys.PSI_ELEMENT.getData(dataContext).asInstanceOf[PsiDirectory]
    val modulePath = moduleDir.getVirtualFile.getPath.toFile
    val project = CommonDataKeys.PROJECT.getData(dataContext)
    val projectSettings = CbtProjectSettings.getInstance(project, project.getBasePath)
    projectSettings.extraModules = projectSettings.extraModules :+ modulePath
    println(s"""New project Modules: ${projectSettings.extraModules.map(_.getPath).mkString(",")}""")
  }
}