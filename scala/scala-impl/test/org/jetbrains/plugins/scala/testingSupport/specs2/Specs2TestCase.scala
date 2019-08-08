package org.jetbrains.plugins.scala
package testingSupport
package specs2

import org.jetbrains.plugins.scala.testingSupport.test.{TestConfigurationProducer, TestConfigurationUtil}

/**
  * @author Roman.Shein
  * @since 16.10.2014.
  */
abstract class Specs2TestCase extends ScalaTestingTestCase {
  override protected val configurationProducer: TestConfigurationProducer =
    TestConfigurationUtil.specs2ConfigurationProducer
}
