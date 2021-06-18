package org.jetbrains.sbt
package project
package data
package service

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
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.external._
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import java.io.File
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
                                  scalacClasspath: Seq[File]): Unit = {
      val scalaLibraries = librariesWithScalaRuntimeJar(module)
      val scalaLibraryWithSameVersion = scalaLibraries.find(isSameCompileVersionOrLanguageLevel(compilerVersion, _))

      scalaLibraryWithSameVersion match {
        case Some(library) =>
          if (!library.isScalaSdk) {
            // library created but not yet marked as Scala SDK
            setScalaSdk(library, scalacClasspath)()
          }
        case None                                 =>
          showScalaLibraryNotFoundWarning(compilerVersion, module.getName)(project)
      }
    }

    private def isSameCompileVersionOrLanguageLevel(compilerVersion: String, scalaLibrary: Library): Boolean =
      scalaLibrary.compilerVersion.exists { version =>
        version == compilerVersion ||
          ScalaLanguageLevel.findByVersion(version) == ScalaLanguageLevel.findByVersion(compilerVersion)
      }

    private def librariesWithScalaRuntimeJar(module: Module): Iterable[Library] = {
      val delegate = new CollectProcessor[Library] {
        override def accept(library: Library): Boolean = library.hasRuntimeLibrary
      }

      getModifiableRootModel(module)
        .orderEntries
        .librariesOnly
        .forEachLibrary(new UniqueProcessor[Library](delegate))

      delegate.getResults.asScala
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

  case class NotificationException(notificationData: NotificationData, id: ProjectSystemId) extends RuntimeException(
    s"""Notification was shown during $id module creation.
       |Category: ${notificationData.getNotificationCategory}
       |Title: ${notificationData.getTitle}
       |Message: ${notificationData.getMessage}
       |NotificationSource: ${notificationData.getNotificationSource}
       |""".stripMargin
  )

  private def showScalaLibraryNotFoundWarning(version: String, module: String)
                                             (implicit project: Project): Unit = {
    showWarning(
      NlsString(SbtBundle.message("sbt.notificationGroupTitle")),
      NlsString(SbtBundle.message("sbt.dataService.scalaLibraryIsNotFound", module, version))
    )
  }

  private def showWarning(title: NlsString, message: NlsString)
                         (implicit project: Project): Unit = {
    val notificationData = new NotificationData(
      title.nls,
      message.nls,
      NotificationCategory.WARNING,
      NotificationSource.PROJECT_SYNC
    )
    notificationData.setBalloonGroup(SbtBundle.message("sbt.notificationGroupName"))

    val systemId = SbtProjectSystem.Id
    if (ApplicationManager.getApplication.isUnitTestMode)
      throw NotificationException(notificationData, systemId)
    else {
      // TODO: maybe show notification in Build (where all the other importing progress is shown) and not in the "Messages" tool window
      ExternalSystemNotificationManager.getInstance(project).showNotification(systemId, notificationData)
    }
  }
}
