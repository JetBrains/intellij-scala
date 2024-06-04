package org.jetbrains.sbt.project.extensionPoints

import com.intellij.execution.configurations.ModuleBasedConfiguration
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

@Internal
trait ModuleBasedConfigurationMainClassExtractor {
  def getConfigurationMainClass(config: ModuleBasedConfiguration[_, _]): Option[String]
}

object ModuleBasedConfigurationMainClassExtractor
  extends ExtensionPointDeclaration[ModuleBasedConfigurationMainClassExtractor]("com.intellij.sbt.configurationMainClassExtractor") {

  def getMainClass(config: ModuleBasedConfiguration[_, _]): Option[String] =
    implementations
      .map(_.getConfigurationMainClass(config))
      .collectFirst { case Some(result) => result }

}
