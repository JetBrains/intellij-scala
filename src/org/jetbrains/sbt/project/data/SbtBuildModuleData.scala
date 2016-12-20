package org.jetbrains.sbt
package project.data

import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys, ProjectSystemId}
import org.jetbrains.sbt.resolvers.SbtResolver

/**
 * @author Pavel Fatin
 */
class SbtBuildModuleData(val owner: ProjectSystemId, val imports: Seq[String], val resolvers: Set[SbtResolver])
        extends AbstractExternalEntityData(owner)

object SbtBuildModuleData {
  val Key: Key[SbtBuildModuleData] = new Key(classOf[SbtBuildModuleData].getName,
    ProjectKeys.MODULE.getProcessingWeight + 1)
}