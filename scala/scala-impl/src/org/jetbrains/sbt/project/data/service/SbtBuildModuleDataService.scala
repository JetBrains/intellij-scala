package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.project.data.SbtBuildModuleData
import org.jetbrains.sbt.project.module.SbtModule
import org.jetbrains.sbt.resolvers.{SbtIndexesManager, SbtResolver}

/**
 * @author Pavel Fatin
 */
class SbtBuildModuleDataService extends AbstractDataService[SbtBuildModuleData, Module](SbtBuildModuleData.Key) {
  override def createImporter(toImport: Seq[DataNode[SbtBuildModuleData]],
                              projectData: ProjectData,
                              project: Project,
                              modelsProvider: IdeModifiableModelsProvider): Importer[SbtBuildModuleData] =
    new SbtBuildModuleDataService.Importer(toImport, projectData, project, modelsProvider)
}

object SbtBuildModuleDataService {
  private class Importer(dataToImport: Seq[DataNode[SbtBuildModuleData]],
                         projectData: ProjectData,
                         project: Project,
                         modelsProvider: IdeModifiableModelsProvider)
    extends AbstractImporter[SbtBuildModuleData](dataToImport, projectData, project, modelsProvider) {

    override def importData(): Unit =
      dataToImport.foreach { moduleNode =>
        for {
          module <- getIdeModuleByNode(moduleNode)
        } {
          val data = moduleNode.getData
          SbtModule.setImportsTo(module, data.imports)
          setResolvers(module, data.resolvers)
          SbtModule.setBuildForModule(module, data.buildFor.id, data.buildFor.buildURI)
        }
      }

    private def setResolvers(module: Module, resolvers: Set[SbtResolver]): Unit = {
      SbtModule.setResolversTo(module, resolvers)
      for {
        localIvyResolver <- resolvers.find(_.name == "Local cache")
        indexesManager <- SbtIndexesManager.getInstance(module.getProject)
      } indexesManager.scheduleLocalIvyIndexUpdate(localIvyResolver)
    }

  }
}
