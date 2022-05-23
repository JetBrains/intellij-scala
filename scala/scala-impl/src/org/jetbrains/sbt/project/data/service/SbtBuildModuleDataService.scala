package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.external.ScalaAbstractProjectDataService
import org.jetbrains.sbt.project.data.SbtBuildModuleData
import org.jetbrains.sbt.project.module.SbtModule

import java.util
import scala.jdk.CollectionConverters._

final class SbtBuildModuleDataService extends ScalaAbstractProjectDataService[SbtBuildModuleData, Module](SbtBuildModuleData.Key) {

  override def importData(
    toImport: util.Collection[_ <: DataNode[SbtBuildModuleData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit =
    for {
      moduleNode <- toImport.asScala
      module     <- modelsProvider.getIdeModuleByNode(moduleNode)
    } importData(module, moduleNode.getData)

  private def importData(sbtModule: Module,
                         data: SbtBuildModuleData): Unit = {
    val SbtBuildModuleData(imports, resolvers, buildFor) = data

    SbtModule.Imports(sbtModule) = imports
    SbtModule.Resolvers(sbtModule) = resolvers.asScala.toSet
    SbtModule.Build(sbtModule) = buildFor.uri
  }
}
