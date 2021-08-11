package org.jetbrains.sbt.shell

import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.{ProcessAdapter, ProcessEvent}
import com.intellij.execution.runners.{ExecutionEnvironmentBuilder, ProgramRunner}
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.EdtTestUtil
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{AllInPackageTestData, ClassTestData, RegexpTestData, SingleTestData}

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}

/**
  * Created by Roman.Shein on 13.04.2017.
  */
abstract class UseSbtTestRunTest extends SbtProjectPlatformTestCase {

  override def runInDispatchThread(): Boolean = false

  override def setUp(): Unit = {
    EdtTestUtil.runInEdtAndWait { () =>
      super.setUp()
    }
  }

  override def tearDown(): Unit = {
    EdtTestUtil.runInEdtAndWait { () =>
      super.tearDown()
    }
  }

  def testScalaTestSimpleTest(): Unit =
    runSingleTest(ScalaTestingTestCase.getScalaTestTemplateConfig(getProject), "test.scalaTest.SimpleScalaTest",
      "ScalaTest First test", "scalaTest", List("Marker: ScalaTest first test", "[info] - First test"),
      List("Marker: ScalaTest second test"))

  def testSpecs2SimpleTest(): Unit =
    runSingleTest(ScalaTestingTestCase.getSpecs2TemplateConfig(getProject), "test.specs2.SimpleSpecs2", "First test",
      "specs2", List("Marker: Specs2 first test", "[info] Specs2 test should", "[info]   + First test"),
      List("Marker: Specs2 second test"))

  def testUTestSimpleTest(): Unit =
    runSingleTest(ScalaTestingTestCase.getUTestTemplateConfig(getProject), "test.uTest.SimpleUTest", "tests\\First uTest",
      "uTest", List("Marker: uTest first test"), List("Marker: uTest second test", "Marker: uTest prefix test"))

  def testScalaTestWholeSuite(): Unit =
    runWholeSuite(ScalaTestingTestCase.getScalaTestTemplateConfig(getProject), "test.scalaTest.SimpleScalaTest",
      "scalaTest", List("Marker: ScalaTest first test", "Marker: ScalaTest second test", "Marker: ScalaTest prefix test"))

  def testSpecs2WholeSuite(): Unit =
    runWholeSuite(ScalaTestingTestCase.getSpecs2TemplateConfig(getProject), "test.specs2.SimpleSpecs2", "specs2",
      List("Marker: Specs2 first test", "Marker: Specs2 second test", "Marker: Specs2 prefix test"))

  def testUTestWholeSuite(): Unit =
    runWholeSuite(ScalaTestingTestCase.getUTestTemplateConfig(getProject), "test.uTest.SimpleUTest", "uTest",
      List("Marker: uTest first test", "Marker: uTest second test", "Marker: uTest prefix test"))

  def testScalaTestAllInPackage(): Unit =
    runPackage(ScalaTestingTestCase.getScalaTestTemplateConfig(getProject), "test.scalaTest", "scalaTest", inScalaTestPackage)

  def testSpecs2AllInPackage(): Unit =
    runPackage(ScalaTestingTestCase.getSpecs2TemplateConfig(getProject), "test.specs2", "specs2",
      inSpecsPackage)

  //TODO: this test is not taking into account tests in OtherUTest/otherTests because sbt test detection detects only 'test'
  def testUTestAllInPackage(): Unit =
    runPackage(ScalaTestingTestCase.getUTestTemplateConfig(getProject), "test.uTest", "uTest",
      inUTestPackage)

  def testSharedPackage(): Unit =
    runPackage(ScalaTestingTestCase.getScalaTestTemplateConfig(getProject), "test", "scalaTest",
      inScalaTestPackage, inSpecsPackage ++ inUTestPackage)

  def testScalaTestRegExp(): Unit =
    runRegexp(ScalaTestingTestCase.getScalaTestTemplateConfig(getProject), ".*ScalaTest", "ScalaTest First.*", "scalaTest",
      List("Marker: ScalaTest first test", "Marker: ScalaTest prefix test", "Marker: ScalaTest Other first test"),
      List("Marker: ScalaTest second test", "Marker: ScalaTest Other prefix test"))

  def testUTestRegExp(): Unit =
    runRegexp(ScalaTestingTestCase.getUTestTemplateConfig(getProject), ".*UTest", "tests\\\\First.*", "uTest",
      List("Marker: uTest Other first test", "Marker: uTest prefix test", "Marker: uTest Other first test"),
      List("Marker: uTest second test"))

  def testSpecs2RegExp(): Unit =
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
                          moduleName: String, expectedStrings: Seq[String], unexpectedStrings: Seq[String] = Seq(), exampleCount: Int = 3): Unit =
    runRegexps(config, Array(classRegexp), Array(testRegexp), moduleName, expectedStrings, unexpectedStrings, exampleCount)

  protected def runRegexps(config: AbstractTestRunConfiguration, classRegexps: Array[String], testRegexps: Array[String],
                          moduleName: String, expectedStrings: Seq[String], unexpectedStrings: Seq[String] = Seq(),
                           exampleCount: Int = 3): Unit = {
    config.testConfigurationData = RegexpTestData(config, classRegexps, testRegexps)
    config.setModule(ModuleManager.getInstance(getProject).findModuleByName(moduleName))
    runConfig(config, expectedStrings, unexpectedStrings, exampleCount)
  }

  protected def runPackage(config: AbstractTestRunConfiguration, packageFqn: String, moduleName: String,
                           expectedStrings: Seq[String], unexpectedStrings: Seq[String] = Seq()): Unit = {
    config.testConfigurationData = AllInPackageTestData(config, packageFqn)
    config.setModule(ModuleManager.getInstance(getProject).findModuleByName(moduleName))
    runConfig(config, expectedStrings, unexpectedStrings, 2)
  }

  protected def runWholeSuite(config: AbstractTestRunConfiguration, classFqn: String, moduleName: String,
                              expectedStrings: Seq[String], unexpectedStrings: Seq[String] = Seq()): Unit = {
    config.testConfigurationData = ClassTestData(config, classFqn)
    config.setModule(ModuleManager.getInstance(getProject).findModuleByName(moduleName))
    runConfig(config, expectedStrings, unexpectedStrings)
  }

  protected def runSingleTest(config: AbstractTestRunConfiguration, classFqn: String, testName: String, moduleName: String,
                              expectedStrings: Seq[String], unexpectedStrings: Seq[String] = Seq()): Unit = {
    config.testConfigurationData = SingleTestData(config, classFqn, testName)
    val module = ModuleManager.getInstance(getProject).findModuleByName(moduleName)
    assert(module != null, s"Could not find module '$moduleName' in project '$getProject'")
    config.setModule(module)
    runConfig(config, expectedStrings, unexpectedStrings)
  }

  protected def runConfig(config: AbstractTestRunConfiguration, expectedStrings: Seq[String],
                          unexpectedStrings: Seq[String], commandsExpected: Int = 1): Unit = {
    config.testConfigurationData.useSbt = true
    val project = config.getProject
    val sdk = ProjectRootManager.getInstance(project).getProjectSdk
    assert(sdk != null, s"project sdk was null in project ${project.getName}")

    val runComplete = Promise[Int]()

    EdtTestUtil.runInEdtAndWait { () =>
      val executor: Executor = Executor.EXECUTOR_EXTENSION_NAME.findExtension(classOf[DefaultRunExecutor])
      val executionEnvironmentBuilder: ExecutionEnvironmentBuilder =
        new ExecutionEnvironmentBuilder(project, executor)

      // we need this whole setup to get the SbtProcessHandlerWrapper that the run config uses
      // rather than the sbt shell process handler since it isn't terminated by the time the test run completes
      val executionEnvironment = executionEnvironmentBuilder
        .runProfile(config)
        .build()

      val runCompleteListener = new ProcessAdapter {
        override def processTerminated(event: ProcessEvent): Unit =
          if (!runComplete.isCompleted)
          runComplete.success(event.getExitCode)
      }

      val callback = new ProgramRunner.Callback {
        override def processStarted(descriptor: RunContentDescriptor): Unit = {
          descriptor.getProcessHandler.addProcessListener(runCompleteListener)
        }
      }

      executionEnvironment.setCallback(callback)
      executionEnvironment.getRunner.execute(executionEnvironment)
      runner.getConsoleView.flushDeferredText()
    }

    val exitCode = Await.result(runComplete.future, 3.minutes)
    val log = logger.getLog
    assert(exitCode == 0, s"sbt shell completed with nonzero exit code. Full log:\n$log")
    expectedStrings.foreach(str => assert(log.contains(str), s"sbt shell console did not contain expected string '$str'. Full log:\n$log"))
    unexpectedStrings.foreach(str => assert(!log.contains(str), s"sbt shell console contained unexpected string '$str'. Full log:\n$log"))
    val logSplitted = logLines(log)
    val errorline = logSplitted.find(line => line contains SbtProjectPlatformTestCase.errorPrefix)
    assert(errorline.isEmpty, s"log contained errors: $errorline")
  }

  private def logLines(log: String) = log.split("\n").toVector
}
