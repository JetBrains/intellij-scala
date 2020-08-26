package org.jetbrains.sbt
package project.data
package service

import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.compiler.{CompilerConfiguration, CompilerConfigurationImpl}
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.ContentRootDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{LanguageLevelProjectExtension, ProjectRootManager}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.project.IncrementalityType
import org.jetbrains.plugins.scala.project.external.{AbstractDataService, AbstractImporter, Importer, SdkUtils}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.project.sources.SharedSourcesModuleType
import org.jetbrains.sbt.settings.SbtSettings

import scala.jdk.CollectionConverters._

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
      ScalaProjectSettings.getInstance(project).setBasePackages(data.basePackages)
      configureJdk(project, data)
      updateJavaCompilerOptionsIn(project, data.javacOptions.asScala)
      setLanguageLevel(project, data)
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

    private def setLanguageLevel(project: Project, data: SbtProjectData): Unit = executeProjectChangeAction {
      val projectJdk = Option(ProjectRootManager.getInstance(project).getProjectSdk)
      val javaLanguageLevel = SdkUtils.javaLanguageLevelFrom(data.javacOptions.asScala)
        .orElse(projectJdk.flatMap(SdkUtils.defaultJavaLanguageLevelIn))
      javaLanguageLevel.foreach { level =>
        val extension = LanguageLevelProjectExtension.getInstance(project)
        extension.setLanguageLevel(level)
        extension.setDefault(false)
      }
    }

    private def setSbtVersion(project: Project, data: SbtProjectData): Unit =
      Option(SbtSettings.getInstance(project).getLinkedProjectSettings(data.projectPath))
        .foreach(s => s.sbtVersion = data.sbtVersion)

    private def updateIncrementalityType(project: Project): Unit = {
      if (getModules.exists(it => ModuleType.get(it) == SharedSourcesModuleType.instance))
        ScalaCompilerConfiguration.instanceIn(project).incrementalityType = IncrementalityType.SBT
    }

    private def updateJavaCompilerOptionsIn(project: Project, options: collection.Seq[String]): Unit = executeProjectChangeAction {
      val settings = JavacConfiguration.getOptions(project, classOf[JavacConfiguration])

      def contains(values: String*) = values.exists(options.contains)

      def valueOf(name: String): Option[String] =
        Option(options.indexOf(name)).filterNot(-1 == _).flatMap(i => options.lift(i + 1))

      if (contains("-g:none")) {
        settings.DEBUGGING_INFO = false
      }

      if (contains("-nowarn", "-Xlint:none")) {
        settings.GENERATE_NO_WARNINGS = true
      }

      if (contains("-deprecation", "-Xlint:deprecation")) {
        settings.DEPRECATION = true
      }

      valueOf("-target").foreach { target =>
        val compilerSettings = CompilerConfiguration.getInstance(project).asInstanceOf[CompilerConfigurationImpl]
        compilerSettings.setProjectBytecodeTarget(target)
      }

      val customOptions = additionalOptionsFrom(options)

      settings.ADDITIONAL_OPTIONS_STRING = customOptions.mkString(" ")
    }

    private def additionalOptionsFrom(options: collection.Seq[String]): collection.Seq[String] = {
      @NonNls val handledOptions = Set("-g:none", "-nowarn", "-Xlint:none", "-deprecation", "-Xlint:deprecation")

      def removePair(name: String, options: collection.Seq[String]): collection.Seq[String] = {
        val index = options.indexOf(name)

        if (index == -1) options
        else {
          val (prefix, suffix) = options.splitAt(index)
          prefix ++ suffix.drop(2)
        }
      }

      removePair("-source", removePair("-target", options.filterNot(handledOptions.contains)))
    }
  }
}