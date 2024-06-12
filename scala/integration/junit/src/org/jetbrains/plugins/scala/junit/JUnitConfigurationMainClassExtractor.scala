package org.jetbrains.plugins.scala.junit

import com.intellij.execution.configurations.ModuleBasedConfiguration
import org.jetbrains.sbt.project.extensionPoints.ModuleBasedConfigurationMainClassExtractor
import com.intellij.execution.junit.JUnitConfiguration

class JUnitConfigurationMainClassExtractor extends ModuleBasedConfigurationMainClassExtractor {

  override def getConfigurationMainClass(config: ModuleBasedConfiguration[_, _]): Option[String] =
    config match {
      case x: JUnitConfiguration => Option(x.getPersistentData.getMainClassName)
      case _ => None
    }
}
