package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.RunnerAndConfigurationSettings
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{AllInPackageTestData, ClassTestData, SingleTestData}
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, SearchForTest}
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions.assertIsA
import org.junit.Assert
import org.junit.Assert._

trait IntegrationTestConfigAssertions {

  trait ConfigurationAssert extends Function1[RunnerAndConfigurationSettings, Unit]

  def IgnoreConfiguration: ConfigurationAssert = _ => ()

  protected def assertConfigAndSettings(
    configAndSettings: RunnerAndConfigurationSettings,
    testClass: String,
    testNames: String*
  ): Unit = {
    AssertConfigAndSettings(testClass, testNames: _*)(configAndSettings)
  }

  protected def AssertConfigAndSettings(
    testClass: String,
    testNames: String*
  ): ConfigurationAssert = { configAndSettings =>
    val config = configAndSettings.getConfiguration
    assertConfig(testClass, testNames, config.asInstanceOf[AbstractTestRunConfiguration])
  }

  protected def assertPackageConfigAndSettings(
    configAndSettings: RunnerAndConfigurationSettings,
    packageName: String = "",
    generatedName: String = "" // TODO: use (wasn't used from the very beginning
  ): Unit =
    AssertPackageConfigAndSettings(packageName)(configAndSettings)

  protected def AssertPackageConfigAndSettings(packageName: String): ConfigurationAssert = configAndSettings => {
    val config = configAndSettings.getConfiguration
    val testConfig = config.asInstanceOf[AbstractTestRunConfiguration]
    val packageData = assertIsA[AllInPackageTestData](testConfig.testConfigurationData)
    assertEquals("package name are not equal", packageName, packageData.testPackagePath)
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
        assertTrue("test names should be empty for whole-class test run configuration", testNames.isEmpty)
    }

    assertModule(config)
  }

  protected def parseTestName(testName: String): Seq[String] =
    testName.split("\n").map(unescapeTestName).toIndexedSeq

  protected def unescapeTestName(str: String): String =
    TestRunnerUtil.unescapeTestName(str)

  def assertArrayEquals(message: String, expecteds: Seq[String], actuals: Seq[String]): Unit =
    Assert.assertEquals(message, expecteds, actuals)
}
