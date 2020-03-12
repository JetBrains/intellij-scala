package org.jetbrains.bsp.project.test.environment

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.junit.JUnitConfiguration
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestRunConfiguration

object BspTesting {
  def isBspRunnerSupportedConfiguration(config: RunConfiguration): Boolean =
    config match {
      case _: ScalaTestRunConfiguration => true
      case _: JUnitConfiguration => true
      case _ => false
    }
}
