package org.jetbrains.plugins.cbt.project.data.service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationCategory, NotificationData, NotificationSource}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.cbt.structure.CbtModuleExtData
import org.jetbrains.plugins.scala.project.{LibraryExt, ModuleExt, Platform, ScalaLanguageLevel}
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data.service.{AbstractDataService, AbstractImporter, Importer}

class CbtModuleExtDataService extends AbstractDataService[CbtModuleExtData, Library](CbtModuleExtData.Key) {
  override def createImporter(toImport: Seq[DataNode[CbtModuleExtData]],
                              projectData: ProjectData,
                              project: Project,
                              modelsProvider: IdeModifiableModelsProvider): Importer[CbtModuleExtData] =
    new CbtModuleExtDataService.Importer(toImport, projectData, project, modelsProvider)
}

object CbtModuleExtDataService {

  private def showWarning(project: Project, warning: String): Unit = {
    val notification =
      new NotificationData("CBT project import",
        warning,
        NotificationCategory.WARNING,
        NotificationSource.PROJECT_SYNC)
    ExternalSystemNotificationManager.getInstance(project)
      .showNotification(SbtProjectSystem.Id, notification)
  }

  private class Importer(toImport: Seq[DataNode[CbtModuleExtData]],
                         projectData: ProjectData,
                         project: Project,
                         modelsProvider: IdeModifiableModelsProvider)
    extends AbstractImporter[CbtModuleExtData](toImport, projectData, project, modelsProvider) {

    override def importData(): Unit = {
      dataToImport.foreach(doImport)
    }

    def doImport(dataNode: DataNode[CbtModuleExtData]): Unit =
      getIdeModuleByNode(dataNode).foreach { module =>
        val data = dataNode.getData
        module.configureScalaCompilerSettingsFrom("CBT", data.scalacOptions)
        val scalaLibraries = getScalaLibraries(module, Platform.Scala)
        val default = scalaLibraries
          .find(_.scalaVersion.exists(_.toLanguageLevel == data.scalaVersion.toLanguageLevel))
        val scalaLib = scalaLibraries
          .find(_.scalaVersion.contains(data.scalaVersion))
          .orElse(default)
        scalaLib match {
          case Some(lib) =>
            setScalaSdk(lib, Platform.Scala, ScalaLanguageLevel.Default, data.scalacClasspath)
          case None =>
            showWarning(project, s"Can not find scala library ${data.scalaVersion.toString}")
        }
        val model = getModifiableRootModel(module)
        model.inheritSdk()
      }
  }

}