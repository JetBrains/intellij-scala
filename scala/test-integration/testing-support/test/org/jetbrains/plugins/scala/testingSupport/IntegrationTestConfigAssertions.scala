package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.RunnerAndConfigurationSettings
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{AllInPackageTestData, ClassTestData, SingleTestData}
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, SearchForTest}
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions.assertIsA
import org.junit.Assert._
import org.junit.ComparisonFailure

trait IntegrationTestConfigAssertions {

  protected def assertConfigAndSettings(
    configAndSettings: RunnerAndConfigurationSettings,
    testClass: String,
    testNames: String*
  ): Unit = {
    val config = configAndSettings.getConfiguration
    assertConfig(testClass, testNames, config.asInstanceOf[AbstractTestRunConfiguration])
  }

  protected def assertPackageConfigAndSettings(
    configAndSettings: RunnerAndConfigurationSettings,
    packageName: String,
    generatedConfigName: String
  ): Unit = {
    assertPackageConfigAndSettings(configAndSettings, packageName)
    assertGeneratedConfigName(configAndSettings, generatedConfigName)
  }

  private def assertPackageConfigAndSettings(
    configAndSettings: RunnerAndConfigurationSettings,
    packageName: String,
  ): Unit = {
    val config = configAndSettings.getConfiguration
    val testConfig = config.asInstanceOf[AbstractTestRunConfiguration]
    val packageData = assertIsA[AllInPackageTestData](testConfig.testConfigurationData)
    assertEquals("package name are not equal", packageName, packageData.testPackagePath)
  }

  private def assertGeneratedConfigName(
    configAndSettings: RunnerAndConfigurationSettings,
    expectedName: String
  ): Unit = {
    assertEquals("Generated configuration name is wrong", expectedName, configAndSettings.getName)
  }

  private def assertModule(config: AbstractTestRunConfiguration): Unit =
    config.testConfigurationData.searchTest match {
      case SearchForTest.IN_WHOLE_PROJECT =>
      case _ =>
        assertNotNull("module should not be null", config.getModule)
    }

  private def assertConfig(
    testClass: String,
    testNames: Seq[String],
    config: AbstractTestRunConfiguration
  ): Unit = {
    val actualTestClass = config.testConfigurationData.asInstanceOf[ClassTestData].testClassPath
    assertEquals(testClass, actualTestClass)

    config.testConfigurationData match {
      case testData: SingleTestData =>
        val configTests = parseTestName(testData.testName)
        assertArrayEquals("test names should be the same as expected", testNames, configTests)
      case _: ClassTestData =>
        assertArrayEquals("test names should be empty for whole-class test run configuration", testNames, Nil)
    }

    assertModule(config)
  }

  protected def parseTestName(testName: String): Seq[String] =
    testName.split("\n").map(unescapeTestName).toIndexedSeq

  protected def unescapeTestName(str: String): String =
    TestRunnerUtil.unescapeTestName(str)

  def assertArrayEquals(message: String, expected: Seq[String], actual: Seq[String]): Unit = {
    if (expected != actual) {
      val expectedConcatenated = expected.mkString("\n")
      val actualConcatenated = actual.mkString("\n")
      throw new ComparisonFailure(message, expectedConcatenated, actualConcatenated)
    }
  }
}
