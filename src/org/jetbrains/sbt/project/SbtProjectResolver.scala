package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.externalSystem.model.{ProjectKeys, DataNode}
import java.io.File
import settings._

class SbtProjectResolver extends ExternalSystemProjectResolver[SbtExecutionSettings] {
  def resolveProjectInfo(id: ExternalSystemTaskId, projectPath: String, downloadLibraries: Boolean, settings: SbtExecutionSettings) = {
    val path = {
      val file = new File(projectPath)
      if (file.isDirectory) file.getPath else file.getParent
    }

    val projectData = new ProjectData(SbtProjectSystemId, path, path)
    projectData.setName("SomeProject")

    val moduleData = new ModuleData(SbtProjectSystemId, StdModuleTypes.JAVA.getId, "SomeModule", projectData.getIdeProjectFileDirectoryPath)

    val projectNode = new DataNode[ProjectData](ProjectKeys.PROJECT, projectData, null)

    val moduleDataNode = projectNode.createChild(ProjectKeys.MODULE, moduleData)

    projectNode
  }
}
