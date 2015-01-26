package org.jetbrains.plugins.scala
package testingSupport.specs2

import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.testingSupport.test.specs2.{Specs2RunConfiguration, Specs2ConfigurationProducer}
import com.intellij.execution.RunnerAndConfigurationSettings

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
abstract class Specs2TestCase extends ScalaTestingTestCase(new Specs2ConfigurationProducer){

  override protected def checkConfigAndSettings(configAndSettings: RunnerAndConfigurationSettings,
                                                testClass: String,
                                                testName: Option[String]) = {
    val config = configAndSettings.getConfiguration
    assert(config.isInstanceOf[Specs2RunConfiguration])
    val specsConfig = config.asInstanceOf[Specs2RunConfiguration]
    checkConfig(testClass, testName, specsConfig)
  }

}
