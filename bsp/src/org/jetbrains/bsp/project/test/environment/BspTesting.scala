package org.jetbrains.bsp.project.test.environment

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.junit.JUnitConfiguration
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestRunConfiguration

import scala.collection.JavaConverters._

object BspTesting {
  def isBspRunnerSupportedConfiguration(config: RunConfiguration): Boolean =
    BspEnvironmentRunnerExtension.implementations.exists(_.runConfigurationSupported(config))
}
