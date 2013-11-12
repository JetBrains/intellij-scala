package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.model.{ProjectKeys, Key, ProjectSystemId}
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData

/**
 * @author Pavel Fatin
 */
class ScalaFacetData(val owner: ProjectSystemId,
                     val scalaVersion: String,
                     val basePackage: String,
                     val compilerLibraryName: String,
                     val compilerOptions: Seq[String]) extends AbstractExternalEntityData(owner)

object ScalaFacetData {
  val Key: Key[ScalaFacetData] = new Key(classOf[ScalaFacetData].getName,
    ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 1)
}