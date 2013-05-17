package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService
import java.util
import com.intellij.openapi.externalSystem.model.{Key, DataNode}
import com.intellij.openapi.project.Project

/**
 * @author Pavel Fatin
 */
class SbtProjectDataService extends ProjectDataService[SbtData, Project] {
  def getTargetDataKey = new Key(classOf[SbtData].getName)

  def importData(toImport: util.Collection[DataNode[SbtData]], project: Project, synchronous: Boolean) {}

  def removeData(toRemove: util.Collection[_ <: Project], project: Project, synchronous: Boolean) {}
}

class SbtData