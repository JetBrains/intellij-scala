package org.jetbrains.plugins.scala
package testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.{ScalaTestRunConfiguration, ScalaTestConfigurationProducer}
import com.intellij.execution.RunnerAndConfigurationSettings
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration

/**
 * @author Roman.Shein
 * @since 09.10.2014.
 */
abstract class ScalaTestTestCase extends ScalaTestingTestCase(new ScalaTestConfigurationProducer()){

  override protected def checkConfigAndSettings(configAndSettings: RunnerAndConfigurationSettings,
                                       testClass: String,
                                       testName: Option[String] = None) = {
    val config = configAndSettings.getConfiguration
    assert(config.isInstanceOf[ScalaTestRunConfiguration])
    val scalaTestConfig = config.asInstanceOf[ScalaTestRunConfiguration]
    checkConfig(testClass, testName, scalaTestConfig)
  }
}
