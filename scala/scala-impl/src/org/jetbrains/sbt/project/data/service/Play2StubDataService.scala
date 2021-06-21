package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.external.ScalaAbstractProjectDataService
import org.jetbrains.sbt.project.data.SbtPlay2ProjectData

import java.util

class Play2StubDataService extends ScalaAbstractProjectDataService[SbtPlay2ProjectData, Project](SbtPlay2ProjectData.Key) {

  override def importData(
    toImport: util.Collection[_ <: DataNode[SbtPlay2ProjectData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = ()
}
