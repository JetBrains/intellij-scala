package org.jetbrains.sbt
package project
package data
package service

import java.io.File

import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectSystemId}
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationCategory, NotificationData, NotificationSource}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl
import com.intellij.openapi.roots.libraries.Library
import com.intellij.util.CommonProcessors.CollectProcessor
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.external._

import scala.collection.JavaConverters

/**
 * @author Pavel Fatin
 */
final class ModuleExtDataService extends AbstractDataService[ModuleExtData, Library](ModuleExtData.Key) {
  override def createImporter(toImport: Seq[DataNode[ModuleExtData]],
                              projectData: ProjectData,
                              project: Project,
                              modelsProvider: IdeModifiableModelsProvider): Importer[ModuleExtData] =
    new ModuleExtDataService.Importer(toImport, projectData, project, modelsProvider)
}

object ModuleExtDataService {

  private class Importer(dataToImport: Seq[DataNode[ModuleExtData]],
                         projectData: ProjectData,
                         project: Project,
                         modelsProvider: IdeModifiableModelsProvider)
    extends AbstractImporter[ModuleExtData](dataToImport, projectData, project, modelsProvider) {

    override def importData(): Unit = for {
      dataNode <- dataToImport
      module <- getIdeModuleByNode(dataNode)
      ModuleExtData(scalaVersion, scalacClasspath, scalacOptions, sdk, javacOptions) = dataNode.getData
    } {
      module.configureScalaCompilerSettingsFrom("sbt", scalacOptions)
      scalaVersion.foreach(configureScalaSdk(module, _, scalacClasspath))
      configureOrInheritSdk(module, sdk)
      configureLanguageLevel(module, javacOptions)
      configureJavacOptions(module, javacOptions)
    }

    private def configureScalaSdk(module: Module,
                                  compilerVersion: Version,
                                  scalacClasspath: Seq[File]): Unit = getScalaLibraries(module) match {
      case scalaLibraries if scalaLibraries.isEmpty =>
      case scalaLibraries =>
        val scalaLibrary = scalaLibraries
          .find(_.scalaVersion.contains(compilerVersion))
          .orElse(scalaLibraries.find(_.scalaVersion.exists(_.toLanguageLevel == compilerVersion.toLanguageLevel)))

        scalaLibrary match {
          case Some(library) if !library.isScalaSdk =>
            setScalaSdk(library, scalacClasspath)()
          case None =>
            showWarning(compilerVersion.presentation, module.getName)(project)
          case _ => // do nothing
        }
    }

    private def getScalaLibraries(module: Module): Set[Library] = {
      val processor = new CollectProcessor[Library]()
      getModifiableRootModel(module)
        .orderEntries()
        .librariesOnly()
        .forEachLibrary(processor)

      import JavaConverters._
      processor.getResults.asScala
        .toSet
        .filter(l => Option(l.getName).exists(_.contains(ScalaLibraryName)))
    }

    private def configureOrInheritSdk(module: Module, sdk: Option[SdkReference]): Unit = {
      val model = getModifiableRootModel(module)
      model.inheritSdk()
      sdk.flatMap(SdkUtils.findProjectSdk).foreach(model.setSdk)
    }

    private def configureLanguageLevel(module: Module, javacOptions: Seq[String]): Unit = {
      val model = getModifiableRootModel(module)
      val moduleSdk = Option(model.getSdk)
      val languageLevel = SdkUtils.javaLanguageLevelFrom(javacOptions)
        .orElse(moduleSdk.flatMap(SdkUtils.defaultJavaLanguageLevelIn))
      languageLevel.foreach { level =>
        val extension = model.getModuleExtension(classOf[LanguageLevelModuleExtensionImpl])
        extension.setLanguageLevel(level)
      }
    }

    private def configureJavacOptions(module: Module, javacOptions: Seq[String]): Unit = {
      for {
        targetPos <- Option(javacOptions.indexOf("-target")).filterNot(_ == -1)
        targetValue <- javacOptions.lift(targetPos + 1)
        compilerSettings = CompilerConfiguration.getInstance(module.getProject)
      } {
        executeProjectChangeAction(compilerSettings.setBytecodeTargetLevel(module, targetValue))
      }
    }
  }

  case class NotificationException(notificationData: NotificationData, id: ProjectSystemId) extends Exception

  private def showWarning(version: String, module: String)
                         (implicit project: Project): Unit = {
    val notificationData = new NotificationData(
      SbtBundle("sbt.notificationGroupTitle"),
      SbtBundle("sbt.dataService.scalaLibraryIsNotFound", version, module),
      NotificationCategory.WARNING,
      NotificationSource.PROJECT_SYNC
    )
    notificationData.setBalloonGroup(SbtBundle("sbt.notificationGroupName"))

    SbtProjectSystem.Id match {
      case systemId if ApplicationManager.getApplication.isUnitTestMode =>
        throw NotificationException(notificationData, systemId)
      case systemId =>
        ExternalSystemNotificationManager.getInstance(project).showNotification(systemId, notificationData)
    }
  }
}
