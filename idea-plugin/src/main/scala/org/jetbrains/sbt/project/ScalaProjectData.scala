package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.model.{Key, ProjectSystemId}
import com.intellij.openapi.externalSystem.model.project.AbstractProjectEntityData
import java.io.File

/**
 * @author Pavel Fatin
 */
class ScalaProjectData(val owner: ProjectSystemId, val javaHome: File) extends AbstractProjectEntityData(owner)

object ScalaProjectData {
  val Key: Key[ScalaProjectData] = new Key(classOf[ScalaProjectData].getName)
}