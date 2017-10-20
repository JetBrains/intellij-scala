package org.jetbrains.plugins.cbt.action

import com.intellij.openapi.actionSystem.{AnActionEvent, CommonDataKeys, LangDataKeys}
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiDirectory
import org.jetbrains.plugins.cbt._
import org.jetbrains.plugins.cbt.project.CbtExtraModuleType
import org.jetbrains.plugins.cbt.project.settings.CbtProjectSettings

import scala.collection.JavaConverters._

class StopConsiderAsCbtModule extends CbtProjectAction {
  override def enabled(e: AnActionEvent): Boolean = {
    val dataContext = e.getDataContext
    val project = CommonDataKeys.PROJECT.getData(dataContext)
    val projectSettings = CbtProjectSettings.getInstance(project, project.getBasePath)

    def moduleExists(dir: PsiDirectory) =
      projectSettings.getModules.asScala
        .map(_.toFile.getName)
        .contains(dir.getName)

    val enabledOpt =
      for {
        module <- Option(dataContext.getData(LangDataKeys.MODULE.getName).asInstanceOf[Module])
        if module.getModuleTypeName == CbtExtraModuleType.ID
        target <- Option(CommonDataKeys.PSI_ELEMENT.getData(dataContext))
      } yield {
        target match {
          case dir: PsiDirectory
            if moduleExists(dir) => true
          case _ => false
        }
      }
    enabledOpt.getOrElse(false)
  }

  override def actionPerformed(e: AnActionEvent): Unit = {
    val dataContext = e.getDataContext
    val moduleDir = CommonDataKeys.PSI_ELEMENT.getData(dataContext).asInstanceOf[PsiDirectory]
    val modulePath = moduleDir.getVirtualFile.getPath
    val project = CommonDataKeys.PROJECT.getData(dataContext)
    val projectSettings = CbtProjectSettings.getInstance(project, project.getBasePath)
    if (isModule(projectSettings, modulePath)) {
      projectSettings.extraModules.remove(modulePath)
    }
    project.refresh()
  }
}
