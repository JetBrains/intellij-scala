package org.jetbrains.sbt
package project.data

import com.intellij.openapi.externalSystem.model.{ProjectKeys, Key, ProjectSystemId}
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData

/**
 * @author Pavel Fatin
 */
class SbtModuleData(val owner: ProjectSystemId, val imports: Seq[String]) extends AbstractExternalEntityData(owner)

object SbtModuleData {
  val Key: Key[SbtModuleData] = new Key(classOf[SbtModuleData].getName,
    ProjectKeys.MODULE.getProcessingWeight + 1)
}