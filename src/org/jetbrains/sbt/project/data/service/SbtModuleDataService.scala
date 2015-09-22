package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.project.data.SbtModuleData
import org.jetbrains.sbt.project.module.SbtModule
import org.jetbrains.sbt.resolvers.{SbtResolver, SbtResolverIndexesManager}
import org.jetbrains.plugins.scala.extensions._

/**
 * @author Pavel Fatin
 */
class SbtModuleDataService extends AbstractDataService[SbtModuleData, Module](SbtModuleData.Key) {
  override def createImporter(toImport: Seq[DataNode[SbtModuleData]],
                              projectData: ProjectData,
                              project: Project,
                              modelsProvider: IdeModifiableModelsProvider): Importer[SbtModuleData] =
    new SbtModuleDataService.Importer(toImport, projectData, project, modelsProvider)
}

object SbtModuleDataService {
  private class Importer(dataToImport: Seq[DataNode[SbtModuleData]],
                         projectData: ProjectData,
                         project: Project,
                         modelsProvider: IdeModifiableModelsProvider)
    extends AbstractImporter[SbtModuleData](dataToImport, projectData, project, modelsProvider) {

    override def importData(): Unit =
      dataToImport.foreach { moduleNode =>
        for {
          module <- getIdeModuleByNode(moduleNode)
          imports = moduleNode.getData.imports
          resolvers = moduleNode.getData.resolvers
        } {
          SbtModule.setImportsTo(module, imports)
          setResolvers(module, resolvers)
          updateLocalResolvers(resolvers)
        }
      }

    private def setResolvers(module: Module, resolvers: Set[SbtResolver]): Unit = {
      SbtModule.setResolversTo(module, resolvers)
      resolvers.foreach(SbtResolverIndexesManager().add)
    }

    private def updateLocalResolvers(resolvers: Set[SbtResolver]): Unit = {
      val localResolvers = resolvers.filter { resolver =>
        resolver.associatedIndex.exists(_.isLocal)
      }
      invokeLater(SbtResolverIndexesManager().update(localResolvers.toSeq))
    }
  }
}
