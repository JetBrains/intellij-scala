package org.jetbrains.sbt
package project
package data
package service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.external.{AbstractDataService, AbstractImporter}
import org.jetbrains.sbt.resolvers.{SbtIndexesManager, SbtResolver}

import scala.jdk.CollectionConverters._

/**
  * @author Pavel Fatin
  */
final class SbtBuildModuleDataService extends AbstractDataService[SbtBuildModuleData, Module](SbtBuildModuleData.Key) {

  //noinspection TypeAnnotation
  override def createImporter(toImport: Seq[DataNode[SbtBuildModuleData]],
                              projectData: ProjectData,
                              project: Project,
                              modelsProvider: IdeModifiableModelsProvider) =
    new AbstractImporter[SbtBuildModuleData](toImport, projectData, project, modelsProvider) {

      override def importData(): Unit = for {
        moduleNode <- dataToImport
        module <- getIdeModuleByNode(moduleNode)
      } SbtBuildModuleDataService.importData(module, moduleNode.getData)
    }
}

object SbtBuildModuleDataService {

  private def importData(sbtModule: Module,
                         data: SbtBuildModuleData): Unit = {
    import module.SbtModule._
    val SbtBuildModuleData(imports, resolvers, buildFor) = data

    Imports(sbtModule) = imports
    Resolvers(sbtModule) = resolvers.asScala
    setLocalIvyCache(resolvers)(sbtModule.getProject)
    Build(sbtModule) = buildFor
  }

  private[this] def setLocalIvyCache(resolvers: java.util.Set[SbtResolver])
                                    (implicit project: Project): Unit =
    for {
      localIvyResolver <- resolvers.asScala.find(_.name == "Local cache")
      indexesManager <- SbtIndexesManager.getInstance(project)
    } indexesManager.scheduleLocalIvyIndexUpdate(localIvyResolver)
}
