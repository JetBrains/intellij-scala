package org.jetbrains.sbt.project.extensionPoints

import com.intellij.execution.configurations.ModuleBasedConfiguration
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

@Internal
trait ModuleBasedConfigurationDetailsExtractor {
  def getConfigurationMainClass(config: ModuleBasedConfiguration[?, ?]): Option[String]
  def isTestConfiguration(config: ModuleBasedConfiguration[?, ?]): Boolean
}

object ModuleBasedConfigurationDetailsExtractor
  extends ExtensionPointDeclaration[ModuleBasedConfigurationDetailsExtractor]("com.intellij.sbt.configurationDetailsExtractor") {

  def getMainClass(config: ModuleBasedConfiguration[?, ?]): Option[String] =
    implementations
      .map(_.getConfigurationMainClass(config))
      .collectFirst { case Some(result) => result }

  def isTestConfiguration(config: ModuleBasedConfiguration[?, ?]): Boolean =
    implementations
      .exists(_.isTestConfiguration(config))

}
