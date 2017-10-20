package org.jetbrains.plugins.cbt.project.template

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import org.jetbrains.plugins.cbt.project.model.{CbtProjectConverter, CbtProjectInfo}
import org.jetbrains.plugins.cbt.project.settings.CbtExecutionSettings

import scala.util.{Success, Try}
import scala.xml.Node

object CbtProjectImporter {
  def importProject(buildInfo: Node, settings: CbtExecutionSettings): Try[DataNode[ProjectData]] =
    Success(CbtProjectInfo(buildInfo))
      .flatMap(CbtProjectConverter(_, settings))
}
