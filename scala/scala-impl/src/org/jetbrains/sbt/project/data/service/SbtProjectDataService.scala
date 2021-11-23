package org.jetbrains.sbt
package project.data
package service

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.ContentRootDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.{LanguageLevelProjectExtension, ProjectRootManager}
import org.jetbrains.plugins.scala.project.IncrementalityType
import org.jetbrains.plugins.scala.project.external._
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.project.sources.SharedSourcesModuleType
import org.jetbrains.sbt.settings.SbtSettings

import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala

class SbtProjectDataService extends ScalaAbstractProjectDataService[SbtProjectData, Project](SbtProjectData.Key) {

  override def importData(
    toImport: util.Collection[_ <: DataNode[SbtProjectData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = {
    val dataToImport  = toImport.asScala

    for {
      head <- dataToImport.headOption
      projectData <- Option(ExternalSystemApiUtil.findParent(head, ProjectKeys.PROJECT))
    } {
      projectData.removeUserData(ContentRootDataService.CREATE_EMPTY_DIRECTORIES) //we don't need creating empty dirs anyway
    }

    dataToImport.foreach(node => doImport(project, node.getData, modelsProvider))
  }

  private def doImport(project: Project, data: SbtProjectData, modelsProvider: IdeModifiableModelsProvider): Unit = {
    SbtProjectSettings.forProject(project).foreach(_.converterVersion = SbtProjectSettings.ConverterVersion)
    configureJdk(project, data)
    setDefaultJavacOptions(project)
    setSbtVersion(project, data)
    updateIncrementalityType(project, modelsProvider)
  }

  private def configureJdk(project: Project, data: SbtProjectData): Unit = executeProjectChangeAction(project) {
    val existingJdk = Option(ProjectRootManager.getInstance(project).getProjectSdk)
    val projectJdk: Option[Sdk] =
      Option(data.jdk)
        .flatMap(SdkUtils.findProjectSdk)
        .orElse(existingJdk)
        .orElse(SdkUtils.mostRecentJdk)
    projectJdk.foreach(ProjectRootManager.getInstance(project).setProjectSdk)
  }


  private def setDefaultJavacOptions(project: Project): Unit = executeProjectChangeAction(project) {
    // special options -source, -target OR --release
    setDefaultProjectLanguageLevel(project)
    setDefaultTargetBytecodeLevel(project)

    val javacOptions = JavacConfiguration.getOptions(project, classOf[JavacConfiguration])
    javacOptions.ADDITIONAL_OPTIONS_STRING = ""
  }

  // NOTE: looks like this will not effect anything in sbt projects,
  // because each module (including root module) has explicitly set java language level
  // so project language level should not be used
  private def setDefaultProjectLanguageLevel(project: Project): Unit = {
    val projectJdk = Option(ProjectRootManager.getInstance(project).getProjectSdk)
    val jdkLanguageLevel = projectJdk.flatMap(SdkUtils.defaultJavaLanguageLevelIn)
    jdkLanguageLevel.foreach { level =>
      val extension = LanguageLevelProjectExtension.getInstance(project)
      extension.setLanguageLevel(level)
      extension.setDefault(true)
    }
  }

  private def setDefaultTargetBytecodeLevel(project: Project): Unit = {
    val compilerSettings = CompilerConfiguration.getInstance(project)
    compilerSettings.setProjectBytecodeTarget(null) // means "same as language level"
  }

  private def setSbtVersion(project: Project, data: SbtProjectData): Unit =
    Option(SbtSettings.getInstance(project).getLinkedProjectSettings(data.projectPath))
      .foreach(s => s.sbtVersion = data.sbtVersion)

  private def updateIncrementalityType(project: Project, modelsProvider: IdeModifiableModelsProvider): Unit = {
    val modules = modelsProvider.getModules
    if (modules.exists(it => ModuleType.get(it) == SharedSourcesModuleType.instance))
      ScalaCompilerConfiguration.instanceIn(project).incrementalityType = IncrementalityType.SBT
  }
}