package org.jetbrains.plugins.scala
package testingSupport.specs2

import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.specs2.Specs2ConfigurationProducer

/**
  * @author Roman.Shein
  * @since 16.10.2014.
  */
abstract class Specs2TestCase extends ScalaTestingTestCase {
  override protected val configurationProducer: AbstractTestConfigurationProducer = new Specs2ConfigurationProducer
}
