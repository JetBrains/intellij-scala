package org.jetbrains.plugins.scala.testingSupport.utest

import com.intellij.execution.RunnerAndConfigurationSettings
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.testingSupport.test.utest.{UTestRunConfiguration, UTestConfigurationProducer}

/**
  * @author Roman.Shein
  * @since 13.05.2015.
  */
abstract class UTestTestCase extends ScalaTestingTestCase(new UTestConfigurationProducer) {
}
