package org.jetbrains.sbt
package project
package data
package service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.external.ScalaAbstractProjectDataService
import org.jetbrains.sbt.resolvers.{SbtIndexesManager, SbtResolver}

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
    import module.SbtModule._
    val SbtBuildModuleData(imports, resolvers, buildFor) = data

    Imports(sbtModule) = imports
    Resolvers(sbtModule) = resolvers.asScala.toSet
    setLocalIvyCache(resolvers)(sbtModule.getProject)
    Build(sbtModule) = buildFor
  }

  private[this] def setLocalIvyCache(resolvers: java.util.Set[SbtResolver])
                                    (implicit project: Project): Unit =
    for {
      localIvyResolver <- resolvers.asScala.find(_.name == "Local cache")
      indexesManager   <- SbtIndexesManager.getInstance(project)
    } indexesManager.scheduleLocalIvyIndexUpdate(localIvyResolver)
}
