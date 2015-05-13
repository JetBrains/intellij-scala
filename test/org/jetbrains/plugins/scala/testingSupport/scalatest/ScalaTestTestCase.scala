package org.jetbrains.plugins.scala
package testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.{ScalaTestRunConfiguration, ScalaTestConfigurationProducer}
import com.intellij.execution.RunnerAndConfigurationSettings
import org.jetbrains.plugins.scala.lang.structureView.elements.impl.TestStructureViewElement._
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider

/**
 * @author Roman.Shein
 * @since 09.10.2014.
 */
abstract class ScalaTestTestCase extends ScalaTestingTestCase(new ScalaTestConfigurationProducer()){

  override protected def checkConfigAndSettings(configAndSettings: RunnerAndConfigurationSettings,
                                       testClass: String,
                                       testName: Option[String] = None) = {
    val config = configAndSettings.getConfiguration
    assert(config.isInstanceOf[ScalaTestRunConfiguration])
    val scalaTestConfig = config.asInstanceOf[ScalaTestRunConfiguration]
    checkConfig(testClass, testName, scalaTestConfig)
  }

  override protected def runFileStructureViewTest(testClassName: String, status: Int, tests: String*): Unit = {
    super.runFileStructureViewTest(testClassName, status, (if (status == ignoredStatusId) {
      tests.map(_ + TestNodeProvider.ignoredSuffix)
    } else if (status == pendingStatusId) {
      tests.map(_ + TestNodeProvider.pendingSuffix)
    } else tests):_*)
  }
}
