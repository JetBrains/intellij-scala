package org.jetbrains.sbt
package project.data

import java.util

import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.{PlatformFacade, ProjectStructureHelper}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.project.module.SbtModule
import org.jetbrains.sbt.resolvers.{SbtResolverIndex, SbtResolverIndexesManager}

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class SbtModuleDataService(platformFacade: PlatformFacade, val helper: ProjectStructureHelper)
  extends AbstractDataService[SbtModuleData, Module](SbtModuleData.Key)
  with SafeProjectStructureHelper {

  def doImportData(toImport: util.Collection[DataNode[SbtModuleData]], project: Project) {
    toImport.asScala.foreach { moduleNode =>
      for {
        module <- getIdeModuleByNode(moduleNode, project)
        moduleData = moduleNode.getData
      } {
        SbtModule.setImportsTo(module, moduleData.imports)
        SbtModule.setResolversTo(module, moduleData.resolvers)

        moduleData.resolvers foreach { _ => SbtResolverIndexesManager().add(_) }
        val localResolvers = moduleData.resolvers.toSeq.filter {
          _.associatedIndex.exists { i => i.isLocal && i.timestamp == SbtResolverIndex.NO_TIMESTAMP }
        }
        SbtResolverIndexesManager().update(localResolvers)
      }
    }
  }

  def doRemoveData(toRemove: util.Collection[_ <: Module], project: Project) {}
}
