package org.jetbrains.plugins.cbt.action

import com.intellij.ide.fileTemplates.actions.{AttributesDefaults, CreateFromTemplateActionBase}
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager}
import com.intellij.openapi.actionSystem.{AnActionEvent, CommonDataKeys, DataContext}
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.util.BooleanFunction
import org.jetbrains.plugins.cbt.project.CbtProjectSystem
import org.jetbrains.plugins.cbt.structure.CbtModuleExtData
import org.jetbrains.plugins.scala.icons.Icons

class NewCbtBuildFileAction
  extends CreateFromTemplateActionBase("CBT Build Class", "Create new CBT build Class", Icons.CLASS)
    with CbtProjectAction {

  override def getTemplate(project: Project, dir: PsiDirectory): FileTemplate =
    FileTemplateManager.getDefaultInstance.getInternalTemplate("CBT Build Class")

  override def getAttributesDefaults(dataContext: DataContext): AttributesDefaults = {
    val scalaVersion = {
      val project = CommonDataKeys.PROJECT.getData(dataContext)
      val rootModule = ModuleManager.getInstance(project).getSortedModules.last
      val dataManager = ProjectDataManager.getInstance()

      val predicate = new BooleanFunction[DataNode[ModuleData]] {
        override def fun(s: DataNode[ModuleData]): Boolean = s.getData.getExternalName == rootModule.getName
      }
      (for {
        projectInfo <- Option(dataManager.getExternalProjectData(project, CbtProjectSystem.Id, project.getBasePath))
        projectStructure <- Option(projectInfo.getExternalProjectStructure)
        moduleDataNode <- Option(ExternalSystemApiUtil.find(projectStructure, ProjectKeys.MODULE, predicate))
        cbtModuleData <- Option(ExternalSystemApiUtil.find(moduleDataNode, CbtModuleExtData.Key))
      } yield cbtModuleData.getData.scalaVersion.toString).get
    }
    val defaults = new AttributesDefaults("build").withFixedName(true)
    defaults.addPredefined("Scala_version", scalaVersion)
    defaults
  }

  override def enabled(e: AnActionEvent): Boolean = true
}
