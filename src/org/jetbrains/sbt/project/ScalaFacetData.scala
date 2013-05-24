package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.model.{ProjectSystemId, Key}
import com.intellij.openapi.externalSystem.model.project.AbstractProjectEntityData

/**
 * @author Pavel Fatin
 */
class ScalaFacetData(val owner: ProjectSystemId,
                     val scalaVersion: String,
                     val basePackage: String,
                     val compilerLibraryName: String,
                     val compilerOptions: Seq[String]) extends AbstractProjectEntityData(owner)

object ScalaFacetData {
  val Key: Key[ScalaFacetData] = new Key(classOf[ScalaFacetData].getName)
}