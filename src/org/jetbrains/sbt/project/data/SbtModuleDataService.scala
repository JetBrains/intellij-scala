package org.jetbrains.sbt
package project.data

import java.util

import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.{PlatformFacade, ProjectStructureHelper}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.project.module.SbtModule

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class SbtModuleDataService(platformFacade: PlatformFacade, helper: ProjectStructureHelper)
  extends AbstractDataService[SbtModuleData, Module](SbtModuleData.Key) {

  def doImportData(toImport: util.Collection[DataNode[SbtModuleData]], project: Project) {
    toImport.asScala.foreach { moduleNode =>
      val moduleData = moduleNode.getData

      val module = {
        val moduleData: ModuleData = moduleNode.getData(ProjectKeys.MODULE)
        helper.findIdeModule(moduleData.getExternalName, project)
      }

      SbtModule.setImportsTo(module, moduleData.imports)
      SbtModule.setResolversTo(module, moduleData.resolvers)
    }
  }

  def doRemoveData(toRemove: util.Collection[_ <: Module], project: Project) {}
}
