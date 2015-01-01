package org.jetbrains.sbt
package project.data

import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys, ProjectSystemId}

/**
 * @author Pavel Fatin
 */
class ScalaProjectData(val owner: ProjectSystemId,
                       val jdk: Option[ScalaProjectData.Sdk],
                       val javacOptions: Seq[String],
                       val sbtVersion: String,
                       val projectPath: String) extends AbstractExternalEntityData(owner)

object ScalaProjectData {
  val Key: Key[ScalaProjectData] = new Key(classOf[ScalaProjectData].getName,
    ProjectKeys.PROJECT.getProcessingWeight + 1)

  trait Sdk
  case class Jdk(version: String) extends Sdk
  case class Android(version: String) extends Sdk
}