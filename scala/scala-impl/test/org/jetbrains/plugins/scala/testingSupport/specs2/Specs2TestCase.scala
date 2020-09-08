package org.jetbrains.plugins.scala
package testingSupport
package specs2

import com.intellij.execution.actions.RunConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.specs2.Specs2ConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestConfigurationProducer, TestConfigurationUtil}

/**
  * @author Roman.Shein
  * @since 16.10.2014.
  */
abstract class Specs2TestCase extends ScalaTestingTestCase {
  override protected val configurationProducer: AbstractTestConfigurationProducer[_] =
    RunConfigurationProducer.getInstance(classOf[Specs2ConfigurationProducer])
}
