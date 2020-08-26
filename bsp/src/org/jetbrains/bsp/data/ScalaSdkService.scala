package org.jetbrains.bsp.data

import java.io.File

import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectSystemId}
import com.intellij.openapi.externalSystem.service.notification.NotificationData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.external.AbstractImporter

import scala.jdk.CollectionConverters._


class ScalaSdkService extends AbstractProjectDataService[ScalaSdkData, Library] {
  override def getTargetDataKey: Key[ScalaSdkData] = ScalaSdkData.Key

  import scala.jdk.CollectionConverters._

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
        Option(scalaVersion),
        scalacClasspath.asScala
      )
    }

    private def configureScalaSdk(module: Module,
                                  maybeVersion: Option[String],
                                  compilerClasspath: collection.Seq[File]): Unit = for {
      presentation <- maybeVersion
      if ScalaLanguageLevel.findByVersion(presentation).isDefined

      library <- getModifiableRootModel(module)
        .getModuleLibraryTable
        .getLibraries
        .find {
          _.getName.contains(ScalaSdkData.LibraryName)
        }

      if !library.isScalaSdk
    } {
      setScalaSdk(library, compilerClasspath)(Some(presentation))
    }

  }

  case class NotificationException(data: NotificationData, id: ProjectSystemId) extends Exception

}
