package org.jetbrains.sbt.project.data

import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys, ProjectSystemId}

class RunConfigurationData(val owner: ProjectSystemId,
                           val mainClass: String,
                           val homePath: String,
                           val vmOpts: Seq[String],
                           val moduleName: String,
                           val artifacts: Seq[String])
  extends AbstractExternalEntityData(owner)

object RunConfigurationData {
  val Key: Key[RunConfigurationData] = new Key(classOf[RunConfigurationData].getName,
        ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 2)
}