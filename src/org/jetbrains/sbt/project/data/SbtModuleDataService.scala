package org.jetbrains.sbt
package project.data

import java.util

import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.{PlatformFacade, ProjectStructureHelper}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.project.module.SbtModule
import org.jetbrains.sbt.resolvers.{SbtResolver, SbtResolverIndex, SbtResolverIndexesManager}

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class SbtModuleDataService(val helper: ProjectStructureHelper)
  extends AbstractDataService[SbtModuleData, Module](SbtModuleData.Key)
  with SafeProjectStructureHelper {

  def doImportData(toImport: util.Collection[DataNode[SbtModuleData]], project: Project) {
    toImport.asScala.foreach { moduleNode =>
      for {
        module <- getIdeModuleByNode(moduleNode, project)
        imports = moduleNode.getData.imports
        resolvers = moduleNode.getData.resolvers
      } {
        SbtModule.setImportsTo(module, imports)
        setResolvers(module, resolvers)
        updateLocalResolvers(resolvers)
      }
    }
  }

  private def setResolvers(module: Module, resolvers: Set[SbtResolver]): Unit = {
    SbtModule.setResolversTo(module, resolvers)
    resolvers.foreach(SbtResolverIndexesManager().add)
  }

  private def updateLocalResolvers(resolvers: Set[SbtResolver]): Unit = {
    val localResolvers = resolvers.filter { resolver =>
      resolver.associatedIndex.exists { index =>
        index.isLocal && index.timestamp == SbtResolverIndex.NO_TIMESTAMP
      }
    }
    SbtResolverIndexesManager().update(localResolvers.toSeq)
  }

  def doRemoveData(toRemove: util.Collection[_ <: Module], project: Project) {}
}
