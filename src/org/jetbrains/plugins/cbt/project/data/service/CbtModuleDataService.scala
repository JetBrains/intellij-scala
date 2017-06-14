package org.jetbrains.plugins.cbt.project.data.service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.cbt.structure.CbtModuleData
import org.jetbrains.plugins.scala.project.{Platform, ScalaLanguageLevel}
import org.jetbrains.sbt.project.data.service.{AbstractDataService, AbstractImporter, Importer}

class CbtModuleDataService extends AbstractDataService[CbtModuleData, Library](CbtModuleData.Key) {
  override def createImporter(toImport: Seq[DataNode[CbtModuleData]],
                              projectData: ProjectData,
                              project: Project,
                              modelsProvider: IdeModifiableModelsProvider): Importer[CbtModuleData] =
    new CbtModuleDataService.Importer(toImport, projectData, project, modelsProvider)
}

object CbtModuleDataService {

  private class Importer(toImport: Seq[DataNode[CbtModuleData]],
                         projectData: ProjectData,
                         project: Project,
                         modelsProvider: IdeModifiableModelsProvider)
    extends AbstractImporter[CbtModuleData](toImport, projectData, project, modelsProvider) {

    override def importData(): Unit = {
      println("CbtModuleDataService import data called")
      dataToImport.foreach(node => doImport(node))
    }

    def doImport(dataNode: DataNode[CbtModuleData]): Unit = {
      for {
        module <- getIdeModuleByNode(dataNode)
      } {
        val scalaLibraries = getScalaLibraries(module, Platform.Scala)
        println("Scala libs" + scalaLibraries)
        scalaLibraries
          .headOption
          .map(convertToScalaSdk(_, Platform.Scala, ScalaLanguageLevel.Default, dataNode.getData.scalacClasspath))
        val model = getModifiableRootModel(module)
        model.inheritSdk()
      }
    }
  }

}