package org.jetbrains.bsp.data

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import org.jetbrains.plugins.scala.project.external.ScalaAbstractProjectDataService
import com.intellij.openapi.project.Project
import org.jetbrains.bsp.project.BspTargetCanCompile

import java.util

class BspTargetCanCompileDataService extends ScalaAbstractProjectDataService[BspTargetCanCompileData, Project](BspTargetCanCompileData.Key) {

  override def importData(
    toImport: util.Collection[? <: DataNode[BspTargetCanCompileData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = {
    // there will be always one node to import
    toImport.forEach { node =>
      BspTargetCanCompile.getInstance(project).compilableTargets = node.getData.compilableTargets
    }
  }
}
