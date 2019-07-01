package org.jetbrains.plugins.scala
package testingSupport.scalatest

import org.jetbrains.plugins.scala.lang.structureView.element.Test._
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestConfigurationProducer, TestConfigurationUtil}
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider
import org.junit.Assert.fail

/**
  * @author Roman.Shein
  * @since 09.10.2014.
  */
abstract class ScalaTestTestCase extends ScalaTestingTestCase {

  override protected val configurationProducer: AbstractTestConfigurationProducer =
    TestConfigurationUtil.scalaTestConfigurationProducer

  override protected def runFileStructureViewTest(testClassName: String, status: Int, tests: String*): Unit = {
    val testsModified: Seq[String] = status match {
      case NormalStatusId => tests
      case IgnoredStatusId => tests.map(_ + TestNodeProvider.ignoredSuffix)
      case PendingStatusId => tests.map(_ + TestNodeProvider.pendingSuffix)
      case unknownStatus =>
        fail(s"unknown status code: $unknownStatus").asInstanceOf[Nothing]
    }
    super.runFileStructureViewTest(testClassName, status, testsModified: _*)
  }
}
