package org.jetbrains.sbt
package project
package data
package service

import java.io.File
import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectSystemId}
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationCategory, NotificationData, NotificationSource}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl
import com.intellij.openapi.roots.libraries.Library
import com.intellij.util.CommonProcessors.{CollectProcessor, UniqueProcessor}
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.external._
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.jdk.CollectionConverters._

/**
 * @author Pavel Fatin
 */
final class SbtModuleExtDataService extends AbstractDataService[ModuleExtData, Library](ModuleExtData.Key) {
  override def createImporter(toImport: Seq[DataNode[ModuleExtData]],
                              projectData: ProjectData,
                              project: Project,
                              modelsProvider: IdeModifiableModelsProvider): Importer[ModuleExtData] =
    new SbtModuleExtDataService.Importer(toImport, projectData, project, modelsProvider)
}

object SbtModuleExtDataService {

  private class Importer(dataToImport: Seq[DataNode[ModuleExtData]],
                         projectData: ProjectData,
                         project: Project,
                         modelsProvider: IdeModifiableModelsProvider)
    extends AbstractImporter[ModuleExtData](dataToImport, projectData, project, modelsProvider) {

    override def importData(): Unit = for {
      dataNode <- dataToImport
      module <- getIdeModuleByNode(dataNode)
      ModuleExtData(scalaVersion, scalacClasspath, scalacOptions, sdk, javacOptions, packagePrefix, basePackage) = dataNode.getData
    } {
      module.configureScalaCompilerSettingsFrom("sbt", scalacOptions.asScala)
      Option(scalaVersion).foreach(configureScalaSdk(module, _, scalacClasspath.asScala.toSeq))
      configureOrInheritSdk(module, Option(sdk))
      importJavacOptions(module, javacOptions.asScala.toSeq)

      getModifiableRootModel(module).getContentEntries.foreach(_.getSourceFolders.foreach(_.setPackagePrefix(Option(packagePrefix).getOrElse(""))))
      ScalaProjectSettings.getInstance(project).setCustomBasePackage(module.getName, basePackage)
    }

    private def configureScalaSdk(module: Module,
                                  compilerVersion: String,
                                  scalacClasspath: Seq[File]): Unit = getScalaLibraries(module) match {
      case libraries if libraries.isEmpty =>
      case libraries =>
        val maybeLibrary = libraries.asScala.find { library =>
          library.compilerVersion.exists { version =>
            version == compilerVersion ||
              ScalaLanguageLevel.findByVersion(version) == ScalaLanguageLevel.findByVersion(compilerVersion)
          }
        }

        maybeLibrary match {
          case Some(library) if !library.isScalaSdk => setScalaSdk(library, scalacClasspath)()
          case None => showWarning(compilerVersion, module.getName)(project)
          case _ => // do nothing
        }
    }

    private def getScalaLibraries(module: Module) = {
      val delegate = new CollectProcessor[Library] {
        override def accept(library: Library): Boolean = library.hasRuntimeLibrary
      }

      getModifiableRootModel(module)
        .orderEntries
        .librariesOnly
        .forEachLibrary(new UniqueProcessor[Library](delegate))

      delegate.getResults
    }

    private def configureOrInheritSdk(module: Module, sdk: Option[SdkReference]): Unit = {
      val model = getModifiableRootModel(module)
      model.inheritSdk()
      sdk.flatMap(SdkUtils.findProjectSdk).foreach(model.setSdk)
    }

    private def importJavacOptions(module: Module, javacOptions: Seq[String]): Unit = {
      configureLanguageLevel(module, javacOptions)
      configureTargetBytecodeLevel(module, javacOptions)
      configureJavacOptions(module, javacOptions)
    }

    private def configureLanguageLevel(module: Module, javacOptions: Seq[String]): Unit = executeProjectChangeAction {
      val model = getModifiableRootModel(module)
      val moduleSdk = Option(model.getSdk)
      val languageLevelFromJavac = JavacOptionsUtils.javaLanguageLevel(javacOptions)
      val languageLevel = languageLevelFromJavac.orElse(moduleSdk.flatMap(SdkUtils.defaultJavaLanguageLevelIn))
      languageLevel.foreach { level =>
        val extension = model.getModuleExtension(classOf[LanguageLevelModuleExtensionImpl])
        extension.setLanguageLevel(level)
      }
    }

    private def configureTargetBytecodeLevel(module: Module, javacOptions: Seq[String]): Unit = executeProjectChangeAction {
      val targetValueFromJavac = JavacOptionsUtils.effectiveTargetValue(javacOptions)
      val compilerSettings = CompilerConfiguration.getInstance(module.getProject)
      compilerSettings.setBytecodeTargetLevel(module, targetValueFromJavac.orNull)
    }

    private def configureJavacOptions(module: Module, javacOptions0: Seq[String]): Unit = {
      val javacOptions = JavacOptionsUtils.withoutExplicitlyHandledOptions(javacOptions0)

      val compilerSettings = CompilerConfiguration.getInstance(module.getProject)

      val moduleCurrentOptions0 = compilerSettings.getAdditionalOptions(module).asScala.toSeq
      val moduleCurrentOptions: Seq[String] = {
        val moduleCurrentStr = moduleCurrentOptions0.mkString(" ")
        val projectCurrentStr = JavacConfiguration.getOptions(project, classOf[JavacConfiguration]).ADDITIONAL_OPTIONS_STRING

        // NOTE: getAdditionalOptions fallbacks to project options if module options are empty
        // so we assume if they are equal then current module additional options are empty
        if (moduleCurrentStr == projectCurrentStr) Nil
        else moduleCurrentOptions0
      }

      if (javacOptions != moduleCurrentOptions) {
        executeProjectChangeAction {
          compilerSettings.setAdditionalOptions(module, javacOptions.asJava)
        }
      }
    }
  }

  case class NotificationException(notificationData: NotificationData, id: ProjectSystemId) extends Exception

  private def showWarning(version: String, module: String)
                         (implicit project: Project): Unit = {
    val notificationData = new NotificationData(
      SbtBundle.message("sbt.notificationGroupTitle"),
      SbtBundle.message("sbt.dataService.scalaLibraryIsNotFound", version, module),
      NotificationCategory.WARNING,
      NotificationSource.PROJECT_SYNC
    )
    notificationData.setBalloonGroup(SbtBundle.message("sbt.notificationGroupName"))

    SbtProjectSystem.Id match {
      case systemId if ApplicationManager.getApplication.isUnitTestMode =>
        throw NotificationException(notificationData, systemId)
      case systemId =>
        ExternalSystemNotificationManager.getInstance(project).showNotification(systemId, notificationData)
    }
  }
}
