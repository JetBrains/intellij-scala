package org.jetbrains.bsp.data

import java.io.File

import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectSystemId}
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationCategory, NotificationData, NotificationSource}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.bsp.BSP
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.external.{AbstractImporter, SdkReference, SdkUtils}

import scala.collection.JavaConverters._


class ScalaSdkService extends AbstractProjectDataService[ScalaSdkData, Library] {
  def getTargetDataKey: Key[ScalaSdkData] = ScalaSdkData.Key

  import scala.collection.JavaConverters._

  override final def importData(toImport: java.util.Collection[DataNode[ScalaSdkData]],
                                projectData: ProjectData,
                                project: Project,
                                modelsProvider: IdeModifiableModelsProvider): Unit = {

    new ScalaSdkService.Importer(toImport.asScala.toSeq, projectData, project, modelsProvider).importData()
  }

}

object ScalaSdkService {

  private class Importer(dataToImport: Seq[DataNode[ScalaSdkData]],
                         projectData: ProjectData,
                         project: Project,
                         modelsProvider: IdeModifiableModelsProvider)
    extends AbstractImporter[ScalaSdkData](dataToImport, projectData, project, modelsProvider) {

    override def importData(): Unit =
      dataToImport.foreach(doImport)

    private def doImport(dataNode: DataNode[ScalaSdkData]): Unit = for {
      module <- getIdeModuleByNode(dataNode)
      ScalaSdkData(_, scalaVersion, scalacClasspath, scalacOptions) = dataNode.getData
    } {
      module.configureScalaCompilerSettingsFrom("bsp", scalacOptions.asScala)
      configureScalaSdk(
        module,
        scalaVersion,
        scalacClasspath.asScala
      )
    }

    private def configureScalaSdk(module: Module,
                                  maybeVersion: Option[String],
                                  compilerClasspath: Seq[File]): Unit = for {
      presentation <- maybeVersion
      if ScalaLanguageLevel.findByVersion(presentation).isDefined

      library <- getModifiableRootModel(module)
        .getModuleLibraryTable
        .getLibraries
        .find {
          _.getName.contains(ScalaSdkData.LibraryName)
        }

      if !library.isScalaSdk
    } setScalaSdk(library, compilerClasspath)(Some(presentation))

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

    private def showWarning(message: String): Unit = {
      val notification = new NotificationData("bsp Import", message, NotificationCategory.WARNING, NotificationSource.PROJECT_SYNC)
      notification.setBalloonGroup("bsp")
      if (ApplicationManager.getApplication.isUnitTestMode) {
        throw NotificationException(notification, BSP.ProjectSystemId)
      } else {
        ExternalSystemNotificationManager.getInstance(project).showNotification(BSP.ProjectSystemId, notification)
      }
    }

  }

  case class NotificationException(data: NotificationData, id: ProjectSystemId) extends Exception

}
