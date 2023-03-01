package org.jetbrains.bsp.data

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.external.ScalaAbstractProjectDataService
import org.jetbrains.sbt.project.module.SbtModule.{Build, Imports}

import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala

/** See also [[org.jetbrains.sbt.project.data.service.SbtBuildModuleDataService]] */
final class SbtBuildModuleDataBspService extends ScalaAbstractProjectDataService[SbtBuildModuleDataBsp, Module](SbtBuildModuleDataBsp.Key) {

  override def importData(toImport: util.Collection[_ <: DataNode[SbtBuildModuleDataBsp]],
                          projectData: ProjectData,
                          project: Project,
                          modelsProvider: IdeModifiableModelsProvider): Unit = {
    for {
      moduleNode <- toImport.asScala
      module <- modelsProvider.getIdeModuleByNode(moduleNode)
    } {
      val buildModuleData = moduleNode.getData
      Imports(module) = buildModuleData.imports
      Build(module) = buildModuleData.id.uri
    }
  }

}

