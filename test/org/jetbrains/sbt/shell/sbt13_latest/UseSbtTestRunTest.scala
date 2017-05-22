package org.jetbrains.sbt.shell.sbt13_latest

import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.module.ModuleManager
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, TestRunConfigurationForm}
import org.jetbrains.sbt.shell.SbtProjectPlatformTestCase
import org.junit.experimental.categories.Category

/**
  * Created by Roman.Shein on 13.04.2017.
  */
@Category(Array(classOf[SlowTests]))
class UseSbtTestRunTest extends SbtProjectPlatformTestCase {

  def testScalaTestSimpleTest() =
    runSingleTest(ScalaTestingTestCase.getScalaTestTemplateConfig(getProject), "test.scalaTest.SimpleScalaTest",
      "ScalaTest First test", "scalaTest", List("Marker: ScalaTest first test", "[info] - First test"),
      List("Marker: ScalaTest second test"))

  def testSpecs2SimpleTest() =
    runSingleTest(ScalaTestingTestCase.getSpecs2TemplateConfig(getProject), "test.specs2.SimpleSpecs2", "First test",
      "specs2", List("Marker: Specs2 first test", "[info] Specs2 test should", "[info]   + First test"),
      List("Marker: Specs2 second test"))

  def testUTestSimpleTest() =
    runSingleTest(ScalaTestingTestCase.getUTestTemplateConfig(getProject), "test.uTest.SimpleUTest", "tests\\First uTest",
      "uTest", List("Marker: uTest first test"), List("Marker: uTest second test", "Marker: uTest prefix test"))

  def testScalaTestWholeSuite() =
    runWholeSuite(ScalaTestingTestCase.getScalaTestTemplateConfig(getProject), "test.scalaTest.SimpleScalaTest",
      "scalaTest", List("Marker: ScalaTest first test", "Marker: ScalaTest second test", "Marker: ScalaTest prefix test"))

  def testSpecs2WholeSuite() =
    runWholeSuite(ScalaTestingTestCase.getSpecs2TemplateConfig(getProject), "test.specs2.SimpleSpecs2", "specs2",
      List("Marker: Specs2 first test", "Marker: Specs2 second test", "Marker: Specs2 prefix test"))

  def testUTestWholeSuite() =
    runWholeSuite(ScalaTestingTestCase.getUTestTemplateConfig(getProject), "test.uTest.SimpleUTest", "uTest",
      List("Marker: uTest first test", "Marker: uTest second test", "Marker: uTest prefix test"))

  def testScalaTestAllInPackage() =
    runPackage(ScalaTestingTestCase.getScalaTestTemplateConfig(getProject), "test.scalaTest", "scalaTest", inScalaTestPackage)

  def testSpecs2AllInPackage() =
    runPackage(ScalaTestingTestCase.getSpecs2TemplateConfig(getProject), "test.specs2", "specs2",
      inSpecsPackage)

  //TODO: this test is not taking into aaccount tests in OtherUTest/otherTests because SBT test detection detects only 'test'
  def testUTestAllInPackage() =
    runPackage(ScalaTestingTestCase.getUTestTemplateConfig(getProject), "test.uTest", "uTest",
      inUTestPackage)

  def testSharedPackage() =
    runPackage(ScalaTestingTestCase.getScalaTestTemplateConfig(getProject), "test", "scalaTest",
      inScalaTestPackage, inSpecsPackage ++ inUTestPackage)

  def testScalaTestRegExp() =
    runRegexp(ScalaTestingTestCase.getScalaTestTemplateConfig(getProject), ".*ScalaTest", "ScalaTest First.*", "scalaTest",
      List("Marker: ScalaTest first test", "Marker: ScalaTest prefix test", "Marker: ScalaTest Other first test"),
      List("Marker: ScalaTest second test", "Marker: ScalaTest Other prefix test"))

  def testUTestRegExp() =
    runRegexp(ScalaTestingTestCase.getUTestTemplateConfig(getProject), ".*UTest", "tests\\\\First.*", "uTest",
      List("Marker: uTest Other first test", "Marker: uTest prefix test", "Marker: uTest Other first test"),
      List("Marker: uTest second test"))

  def testSpecs2RegExp() =
    runRegexp(ScalaTestingTestCase.getSpecs2TemplateConfig(getProject), ".*Specs2", "First.*", "specs2",
      List("Marker: Specs2 Other first test", "Marker: Specs2 prefix test", "Marker: Specs2 Other first test"),
      List("Marker: Specs2 second test", "Marker: Specs2 Other prefix test"))

  protected val inScalaTestPackage = List("Marker: ScalaTest first test", "Marker: ScalaTest second test", "Marker: ScalaTest prefix test",
    "Marker: ScalaTest Other first test", "Marker: ScalaTest Other prefix test")

  protected val inSpecsPackage = List("Marker: Specs2 first test", "Marker: Specs2 second test", "Marker: Specs2 prefix test",
    "Marker: Specs2 Other first test", "Marker: Specs2 Other prefix test")

  //TODO this is missing from list of all tests: "Marker: nested test", "Marker: uTest Other prefix test"
  protected val inUTestPackage = List("Marker: uTest first test", "Marker: uTest second test", "Marker: uTest prefix test",
    "Marker: uTest Other first test")

  protected def runRegexp(config: AbstractTestRunConfiguration, classRegexp: String, testRegexp: String,
                          moduleName: String, expectedStrings: Seq[String], unexpectedStrings: Seq[String] = Seq(), exampleCount: Int = 3) =
    runRegexps(config, Array(classRegexp), Array(testRegexp), moduleName, expectedStrings, unexpectedStrings, exampleCount)

  protected def runRegexps(config: AbstractTestRunConfiguration, classRegexps: Array[String], testRegexps: Array[String],
                          moduleName: String, expectedStrings: Seq[String], unexpectedStrings: Seq[String] = Seq(),
                           exampleCount: Int = 3) = {
    config.testKind = TestRunConfigurationForm.TestKind.REGEXP
    config.classRegexps = classRegexps
    config.testRegexps = testRegexps
    config.setModule(ModuleManager.getInstance(getProject).findModuleByName(moduleName))
    runConfig(config, expectedStrings, unexpectedStrings, exampleCount)
  }

  protected def runPackage(config: AbstractTestRunConfiguration, packageFqn: String, moduleName: String,
                           expectedStrings: Seq[String], unexpectedStrings: Seq[String] = Seq()) = {
    config.testKind = TestRunConfigurationForm.TestKind.ALL_IN_PACKAGE
    config.setTestPackagePath(packageFqn)
    config.setModule(ModuleManager.getInstance(getProject).findModuleByName(moduleName))
    runConfig(config, expectedStrings, unexpectedStrings, 2)
  }

  protected def runWholeSuite(config: AbstractTestRunConfiguration, classFqn: String, moduleName: String,
                              expectedStrings: Seq[String], unexpectedStrings: Seq[String] = Seq()) = {
    config.testKind = TestRunConfigurationForm.TestKind.CLASS
    config.setTestClassPath(classFqn)
    config.setModule(ModuleManager.getInstance(getProject).findModuleByName(moduleName))
    runConfig(config, expectedStrings, unexpectedStrings)
  }

  protected def runSingleTest(config: AbstractTestRunConfiguration, classFqn: String, testName: String, moduleName: String,
                    expectedStrings: Seq[String], unexpectedStrings: Seq[String] = Seq()) = {
    config.testKind = TestRunConfigurationForm.TestKind.TEST_NAME
    config.testName = testName
    config.setTestClassPath(classFqn)
    config.setModule(ModuleManager.getInstance(getProject).findModuleByName(moduleName))
    runConfig(config, expectedStrings, unexpectedStrings)
  }

  protected def runConfig(config: AbstractTestRunConfiguration, expectedStrings: Seq[String],
                          unexpectedStrings: Seq[String], commandsExpected: Int = 1) = {
    config.useSbt = true
    val executor: Executor = Executor.EXECUTOR_EXTENSION_NAME.findExtension(classOf[DefaultRunExecutor])
    val executionEnvironmentBuilder: ExecutionEnvironmentBuilder =
      new ExecutionEnvironmentBuilder(config.getProject, executor)
    executionEnvironmentBuilder.runProfile(config).buildAndExecute()
    runner.getConsoleView.flushDeferredText()
    val log = logger.getLog
    expectedStrings.foreach(str => assert(log.contains(str), s"Sbt shell console did not contain $str"))
    unexpectedStrings.foreach(str => assert(!log.contains(str), s"Sbt shell console contained $str"))
    assert(!log.contains(SbtProjectPlatformTestCase.errorPrefix))
  }

  override def getPath: String = "sbt/shell/sbtTestRunTest"

}
