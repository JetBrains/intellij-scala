package org.jetbrains.plugins.cbt.project.data.service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.cbt.structure.CbtModuleExtData
import org.jetbrains.plugins.scala.project.{Platform, ScalaLanguageLevel}
import org.jetbrains.sbt.project.data.service.{AbstractDataService, AbstractImporter, Importer}
import org.jetbrains.plugins.scala.project.ModuleExt

class CbtModuleExtDataService extends AbstractDataService[CbtModuleExtData, Library](CbtModuleExtData.Key) {
  override def createImporter(toImport: Seq[DataNode[CbtModuleExtData]],
                              projectData: ProjectData,
                              project: Project,
                              modelsProvider: IdeModifiableModelsProvider): Importer[CbtModuleExtData] =
    new CbtModuleExtDataService.Importer(toImport, projectData, project, modelsProvider)
}

object CbtModuleExtDataService {

  private class Importer(toImport: Seq[DataNode[CbtModuleExtData]],
                         projectData: ProjectData,
                         project: Project,
                         modelsProvider: IdeModifiableModelsProvider)
    extends AbstractImporter[CbtModuleExtData](toImport, projectData, project, modelsProvider) {

    override def importData(): Unit = {
      println("CbtModuleDataService import data called")
      dataToImport.foreach(node => doImport(node))
    }

    def doImport(dataNode: DataNode[CbtModuleExtData]): Unit =
      getIdeModuleByNode(dataNode).foreach { module =>
        val data = dataNode.getData
        module.configureScalaCompilerSettingsFrom("CBT", data.scalacOptions)
        val scalaLibraries = getScalaLibraries(module, Platform.Scala)
        scalaLibraries
          .headOption
          .foreach(setScalaSdk(_, Platform.Scala, ScalaLanguageLevel.Default, data.scalacClasspath))
        val model = getModifiableRootModel(module)
        model.inheritSdk()
      }
  }

}