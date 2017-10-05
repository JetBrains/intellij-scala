package org.jetbrains.plugins.scala.testingSupport.utest

import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestConfigurationProducer, TestConfigurationUtil}

/**
  * @author Roman.Shein
  * @since 13.05.2015.
  */
abstract class UTestTestCase extends ScalaTestingTestCase {
  override protected val configurationProducer: AbstractTestConfigurationProducer =
    TestConfigurationUtil.uTestConfigurationProducer

  protected val testSuiteSecondPrefix = "import utest.framework.TestSuite"
}
