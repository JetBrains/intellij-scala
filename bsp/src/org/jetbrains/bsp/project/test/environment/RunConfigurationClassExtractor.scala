package org.jetbrains.bsp.project.test.environment

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.extensions.ExtensionPointName

object RunConfigurationClassExtractor{
  val EP_NAME: ExtensionPointName[RunConfigurationClassExtractor] =
    ExtensionPointName.create("com.intellij.runConfigurationClassExtractor")
}

trait RunConfigurationClassExtractor {
  def classes(config: RunConfiguration) : Option[List[String]]
  def runConfigurationSupported(config: RunConfiguration): Boolean
}
