package org.jetbrains.sbt
package project.data

import com.intellij.openapi.externalSystem.model.{ProjectKeys, Key, ProjectSystemId}
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import java.io.File

import org.jetbrains.plugins.scala.project.Version

/**
 * @author Pavel Fatin
 */
class ScalaSdkData(val owner: ProjectSystemId,
                   val scalaVersion: Version,
                   val basePackage: String,
                   val compilerClasspath: Seq[File],
                   val compilerOptions: Seq[String]) extends AbstractExternalEntityData(owner)

object ScalaSdkData {
  val Key: Key[ScalaSdkData] = new Key(classOf[ScalaSdkData].getName,
    ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 1)
}