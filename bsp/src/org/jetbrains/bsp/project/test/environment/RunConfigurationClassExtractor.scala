package org.jetbrains.bsp.project.test.environment

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.extensions.ExtensionPointName
import scala.collection.JavaConverters._

sealed trait EnvironmentType

object EnvironmentType{
  case object TEST extends EnvironmentType
  case object RUN extends EnvironmentType
}



object RunConfigurationClassExtractor{
  val EP_NAME: ExtensionPointName[RunConfigurationClassExtractor] =
    ExtensionPointName.create("com.intellij.runConfigurationClassExtractor")

  def getClassExtractor(runConfiguration: RunConfiguration): Option[RunConfigurationClassExtractor] =
    EP_NAME.getExtensionList().asScala
      .find(_.runConfigurationSupported(runConfiguration))

}

trait RunConfigurationClassExtractor {
  def classes(config: RunConfiguration) : Option[List[String]]
  def runConfigurationSupported(config: RunConfiguration): Boolean
  def environmentType: EnvironmentType
}
