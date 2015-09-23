package org.jetbrains.sbt
package project.data

import com.intellij.openapi.externalSystem.model.{ProjectKeys, Key, ProjectSystemId}
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import java.io.File

import org.jetbrains.plugins.scala.project.Version

/**
 * @author Pavel Fatin
 */
class ModuleExtData(val owner: ProjectSystemId,
                    val scalaVersion: Option[Version],
                    val scalacClasspath: Seq[File],
                    val scalacOptions: Seq[String],
                    val jdk: Option[Sdk],
                    val javacOptions: Seq[String]) extends AbstractExternalEntityData(owner)

object ModuleExtData {
  val Key: Key[ModuleExtData] = new Key(classOf[ModuleExtData].getName,
    ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 1)
}