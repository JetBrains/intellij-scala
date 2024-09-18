package org.jetbrains.bsp.project.test.environment

import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.RunConfiguration

class BspEnvironmentApplicationRunnerExtension extends BspEnvironmentRunnerExtension {
  override def classes(config: RunConfiguration): Option[List[String]] = {
    config match {
      case applicationConfig: ApplicationConfiguration => Some(List(applicationConfig.getMainClassName))
      case _ => None
    }
  }

  override def runConfigurationSupported(config: RunConfiguration): Boolean =
    config.isInstanceOf[ApplicationConfiguration]

  override def environmentType: ExecutionEnvironmentType = ExecutionEnvironmentType.RUN
}
