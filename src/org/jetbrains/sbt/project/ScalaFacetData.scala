package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.model.{ProjectSystemId, Key}
import com.intellij.openapi.externalSystem.model.project.AbstractProjectEntityData

/**
 * @author Pavel Fatin
 */
case class ScalaFacetData(owner: ProjectSystemId,
                          scalaVersion: String,
                          basePackage: String,
                          compilerLibraryName: String,
                          compilerOptions: Seq[String]) extends AbstractProjectEntityData(owner)

object ScalaFacetData {
  val Key: Key[ScalaFacetData] = new Key(classOf[ScalaFacetData].getName)
}