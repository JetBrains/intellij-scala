package org.jetbrains.sbt
package project.data

import java.io.File

import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.openapi.externalSystem.model.{ProjectSystemId, ProjectKeys, Key}

/**
 * @author Nikolay Obedin
 * @since 8/12/14.
 */
class AndroidFacetData(val owner: ProjectSystemId,
                       val version: String, val manifest: File, val apk: File,
                       val res: File, val assets: File, val gen: File, val libs: File,
                       val isLibrary: Boolean) extends AbstractExternalEntityData(owner)

object AndroidFacetData {
  val Key: Key[AndroidFacetData] = new Key(classOf[AndroidFacetData].getName,
    ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 1)
}