package org.jetbrains.plugins.scala.junit

import com.intellij.execution.configurations.ModuleBasedConfiguration
import org.jetbrains.sbt.project.extensionPoints.ModuleBasedConfigurationDetailsExtractor
import com.intellij.execution.junit.JUnitConfiguration

class JUnitConfigurationDetailsExtractor extends ModuleBasedConfigurationDetailsExtractor {

  override def getConfigurationMainClass(config: ModuleBasedConfiguration[?, ?]): Option[String] =
    config match {
      case x: JUnitConfiguration => Option(x.getPersistentData.getMainClassName)
      case _ => None
    }

  override def isTestConfiguration(config: ModuleBasedConfiguration[?, ?]): Boolean =
    config match {
      case _: JUnitConfiguration => true
      case _ => false
    }
}
