package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.model.{Key, ProjectSystemId}
import com.intellij.openapi.externalSystem.model.project.AbstractProjectEntityData
import java.io.File

/**
 * @author Pavel Fatin
 */
case class ScalaProjectData(owner: ProjectSystemId, javaHome: File) extends AbstractProjectEntityData(owner)

object ScalaProjectData {
  val Key: Key[ScalaProjectData] = new Key(classOf[ScalaProjectData].getName)
}