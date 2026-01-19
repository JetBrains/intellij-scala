package org.jetbrains.plugins.scala.testingSupport.junit

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.junit.{JUnitConfiguration, TestMethod}
import com.intellij.execution.testframework.AbstractTestProxy
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class ScalaJUnitTestingTestCaseBase extends ScalaTestingTestCase with JUnitIntegrationTestConfigAssertions {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13

  override protected val expectedDefaultRunConfigurationClass: Class[_ <: RunConfiguration] = classOf[JUnitConfiguration]

  protected def assertIsJUnitClassConfiguration(
    settings: RunnerAndConfigurationSettings,
    className: String
  ): Unit = {
    val config = settings.getConfiguration.asInstanceOf[JUnitConfiguration]

    // using fully qualified name because `TestClass` is inaccessible in this place
    org.junit.Assert.assertEquals(
      "Expected test class configuration",
      "com.intellij.execution.junit.TestClass",
      config.getTestObject.getClass.getName
    )

    val data = config.getPersistentData
    org.junit.Assert.assertEquals("Class name", className, data.getMainClassName)
  }

  protected def assertIsJUnitTestMethodConfiguration(
    settings: RunnerAndConfigurationSettings,
    className: String,
    methodName: String
  ): Unit = {
    val config = settings.getConfiguration.asInstanceOf[JUnitConfiguration]

    org.junit.Assert.assertEquals(
      "Expected test method configuration",
      classOf[TestMethod],
      config.getTestObject.getClass
    )

    val data = config.getPersistentData
    org.junit.Assert.assertEquals("Class name", className, data.getMainClassName)
    org.junit.Assert.assertEquals("Method name", methodName, data.getMethodName)
  }

  protected def assertJUnitTestTree(actualRoot: AbstractTestProxy, expected: MyTestTreeNode): Unit = {
    val actual = MyTestTreeNode.fromTestProxy(actualRoot)
    org.junit.Assert.assertEquals("Test console tree", expected, actual)
  }
}