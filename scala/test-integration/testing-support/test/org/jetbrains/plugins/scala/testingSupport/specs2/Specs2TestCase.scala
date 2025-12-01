package org.jetbrains.plugins.scala.testingSupport.specs2

import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.testingSupport.test.specs2.Specs2RunConfiguration

abstract class Specs2TestCase extends ScalaTestingTestCase {

  override protected val expectedDefaultRunConfigurationClass: Class[Specs2RunConfiguration] =
    classOf[Specs2RunConfiguration]
}
