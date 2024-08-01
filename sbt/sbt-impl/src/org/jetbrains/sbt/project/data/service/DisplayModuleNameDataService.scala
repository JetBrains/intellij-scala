package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.external.ScalaAbstractProjectDataService
import org.jetbrains.sbt.project.data.{DisplayModuleNameData, findModuleForParentOfDataNode}
import org.jetbrains.sbt.project.settings.DisplayModuleName

import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala

class DisplayModuleNameDataService extends ScalaAbstractProjectDataService[DisplayModuleNameData, Module](DisplayModuleNameData.Key) {

  override def importData(
    toImport: util.Collection[_ <: DataNode[DisplayModuleNameData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit =
    toImport.asScala.foreach { dataNode =>
      val parentModuleOpt = findModuleForParentOfDataNode(dataNode)
      parentModuleOpt match {
        case Some(module) =>
          val displayModuleName = DisplayModuleName.getInstance(module)
          val displayName = dataNode.getData.name
          displayModuleName.setName(displayName)
        case _ =>
      }
    }
}

