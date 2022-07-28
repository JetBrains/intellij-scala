package org.jetbrains.plugins.scala
package testingSupport
package specs2

import com.intellij.execution.actions.RunConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.specs2.Specs2ConfigurationProducer

abstract class Specs2TestCase extends ScalaTestingTestCase {
  override protected lazy val configurationProducer: AbstractTestConfigurationProducer[_] =
    RunConfigurationProducer.getInstance(classOf[Specs2ConfigurationProducer])
}
