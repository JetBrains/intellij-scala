package org.jetbrains.sbt
package project.data

import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys, ProjectSystemId}

/**
 * @author Pavel Fatin
 */
class SbtProjectData(val owner: ProjectSystemId,
                       val basePackages: Seq[String],
                       val jdk: Option[SbtProjectData.Sdk],
                       val javacOptions: Seq[String],
                       val sbtVersion: String,
                       val projectPath: String) extends AbstractExternalEntityData(owner)

object SbtProjectData {
  val Key: Key[SbtProjectData] = new Key(classOf[SbtProjectData].getName,
    ProjectKeys.MODULE.getProcessingWeight + 1)

  trait Sdk
  case class Jdk(version: String) extends Sdk
  case class Android(version: String) extends Sdk
}