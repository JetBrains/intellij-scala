package org.jetbrains.plugins.scala.testingSupport.junit

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.{ConfigurationContext, RunConfigurationProducer}
import com.intellij.execution.junit.{JUnitConfiguration, TestMethod}
import com.intellij.execution.testframework.AbstractTestProxy
import org.jetbrains.plugins.scala.configurations.TestLocation.CaretLocation
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class ScalaJUnitTestingTestCaseBase extends ScalaTestingTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13

  //not required cause we've overridden `createTestFromCaretLocation`
  override protected def configurationProducer: RunConfigurationProducer[_] = ???

  override protected def createTestFromCaretLocation(caretLocation: CaretLocation): RunnerAndConfigurationSettings =
    inReadAction {
      val psiElement = findPsiElement(caretLocation, getProject, srcPath.toFile)
      val context: ConfigurationContext = new ConfigurationContext(psiElement)
      // automatically detects the preferable configuration producer and creates configuration
      // TODO: remove `configurationProducer` from base class and rewrite base `createTestFromCaretLocation` to this implementation
      //  It will be more close to what intellij does: it search for the appropriate configuration producer from the context automatically
      context.getConfiguration
    }

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