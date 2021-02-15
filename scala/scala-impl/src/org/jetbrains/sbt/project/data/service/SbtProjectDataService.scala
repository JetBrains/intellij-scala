package org.jetbrains.sbt
package project.data
package service

import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.ContentRootDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{LanguageLevelProjectExtension, ProjectRootManager}
import org.jetbrains.plugins.scala.project.IncrementalityType
import org.jetbrains.plugins.scala.project.external._
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.sbt.project.sources.SharedSourcesModuleType
import org.jetbrains.sbt.settings.SbtSettings

/**
 * @author Pavel Fatin
 */
class SbtProjectDataService extends AbstractDataService[SbtProjectData, Project](SbtProjectData.Key) {
  override def createImporter(toImport: Seq[DataNode[SbtProjectData]],
                              projectData: ProjectData,
                              project: Project,
                              modelsProvider: IdeModifiableModelsProvider): Importer[SbtProjectData] = {
    for {
      head <- toImport.headOption
      projectData <- Option(ExternalSystemApiUtil.findParent(head, ProjectKeys.PROJECT))
    } {
      projectData.removeUserData(ContentRootDataService.CREATE_EMPTY_DIRECTORIES) //we don't need creating empty dirs anyway
    }

    new SbtProjectDataService.Importer(toImport, projectData, project, modelsProvider)
  }
}

object SbtProjectDataService {

  private class Importer(dataToImport: Seq[DataNode[SbtProjectData]],
                         projectData: ProjectData,
                         project: Project,
                         modelsProvider: IdeModifiableModelsProvider)
    extends AbstractImporter[SbtProjectData](dataToImport, projectData, project, modelsProvider) {

    override def importData(): Unit =
      dataToImport.foreach(node => doImport(node.getData))

    private def doImport(data: SbtProjectData): Unit = {
      configureJdk(project, data)
      setDefaultProjectLanguageLevel(project)
      setSbtVersion(project, data)
      updateIncrementalityType(project)
    }

    private def configureJdk(project: Project, data: SbtProjectData): Unit = executeProjectChangeAction {
      val existingJdk = Option(ProjectRootManager.getInstance(project).getProjectSdk)
      val projectJdk =
        Option(data.jdk)
          .flatMap(SdkUtils.findProjectSdk)
          .orElse(existingJdk)
          .orElse(SdkUtils.mostRecentJdk)
      projectJdk.foreach(ProjectRootManager.getInstance(project).setProjectSdk)
    }

    // NOTE: looks like this will not effect anything in sbt projects,
    // because each module (including root module) has java language level set and project language level is not used
    private def setDefaultProjectLanguageLevel(project: Project): Unit = executeProjectChangeAction {
      val extension = LanguageLevelProjectExtension.getInstance(project)
      val projectJdk = Option(ProjectRootManager.getInstance(project).getProjectSdk)
      val jdkLanguageLevel = projectJdk.flatMap(SdkUtils.defaultJavaLanguageLevelIn)
      jdkLanguageLevel.foreach { level =>
        extension.setLanguageLevel(level)
        extension.setDefault(true)
      }
    }

    private def setSbtVersion(project: Project, data: SbtProjectData): Unit =
      Option(SbtSettings.getInstance(project).getLinkedProjectSettings(data.projectPath))
        .foreach(s => s.sbtVersion = data.sbtVersion)

    private def updateIncrementalityType(project: Project): Unit = {
      if (getModules.exists(it => ModuleType.get(it) == SharedSourcesModuleType.instance))
        ScalaCompilerConfiguration.instanceIn(project).incrementalityType = IncrementalityType.SBT
    }
  }
}