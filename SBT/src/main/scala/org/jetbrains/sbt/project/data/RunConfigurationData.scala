package org.jetbrains.sbt.project.data

import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys, ProjectSystemId}
import org.jetbrains.sbt.project.structure.RunConfigurationI

class RunConfigurationData(val owner: ProjectSystemId,
                           val configuration: RunConfigurationI)
  extends AbstractExternalEntityData(owner)

object RunConfigurationData {
  val Key: Key[RunConfigurationData] = new Key(classOf[RunConfigurationData].getName,
        ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 2)
}