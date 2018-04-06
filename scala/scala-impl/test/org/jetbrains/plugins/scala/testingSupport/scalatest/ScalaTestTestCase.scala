package org.jetbrains.plugins.scala
package testingSupport.scalatest

import org.jetbrains.plugins.scala.lang.structureView.element.Test._
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestConfigurationProducer, TestConfigurationUtil}
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider

/**
  * @author Roman.Shein
  * @since 09.10.2014.
  */
abstract class ScalaTestTestCase extends ScalaTestingTestCase {

  override protected val configurationProducer: AbstractTestConfigurationProducer =
    TestConfigurationUtil.scalaTestConfigurationProducer

  override protected def runFileStructureViewTest(testClassName: String, status: Int, tests: String*): Unit = {
    super.runFileStructureViewTest(testClassName, status, (if (status == IgnoredStatusId) {
      tests.map(_ + TestNodeProvider.ignoredSuffix)
    } else if (status == PendingStatusId) {
      tests.map(_ + TestNodeProvider.pendingSuffix)
    } else tests): _*)
  }
}
