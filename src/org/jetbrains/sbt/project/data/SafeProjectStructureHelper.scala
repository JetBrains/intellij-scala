package org.jetbrains.sbt.project.data

import com.intellij.openapi.externalSystem.model.{ProjectKeys, DataNode}
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

/**
 * @author Nikolay Obedin
 * @since 6/4/15.
 */
trait SafeProjectStructureHelper {
  val helper: ProjectStructureHelper

  def getIdeModuleByNode(node: DataNode[_], project: Project): Option[Module] =
    for {
      moduleData <- Option(node.getData(ProjectKeys.MODULE))
      module <- Option(helper.findIdeModule(moduleData, project))
    } yield module
}
