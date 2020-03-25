package org.jetbrains.bsp.project.test.environment

import com.intellij.execution.configurations.RunConfiguration
import org.jetbrains.bsp.BSP
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestRunConfiguration

object BspTesting {
  def isBspRunnerSupportedConfiguration(config: RunConfiguration) = config match {
    case _: ScalaTestRunConfiguration => true
    case _ => false
  }
}
