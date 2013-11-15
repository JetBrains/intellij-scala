package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.model.{ProjectKeys, Key, ProjectSystemId}
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import java.io.File

/**
 * @author Pavel Fatin
 */
class ScalaProjectData(val owner: ProjectSystemId, val javaHome: File) extends AbstractExternalEntityData(owner)

object ScalaProjectData {
  val Key: Key[ScalaProjectData] = new Key(classOf[ScalaProjectData].getName,
    ProjectKeys.PROJECT.getProcessingWeight + 1)
}