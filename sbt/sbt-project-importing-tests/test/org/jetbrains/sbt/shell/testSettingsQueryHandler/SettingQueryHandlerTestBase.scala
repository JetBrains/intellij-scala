package org.jetbrains.sbt.shell.testSettingsQueryHandler

import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.SbtUtil.SbtProjectUriAndId
import org.jetbrains.sbt.project.SbtProjectStructureImportingLike
import org.jetbrains.sbt.shell.testSettingsQueryHandler.SbtProjectPlatformTestCase.ProcessLogger
import org.jetbrains.sbt.shell.testSettingsQueryHandler.SettingQueryHandlerTestBase.{SbtSetCommand, SbtSetCommandSettingPath}
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellCommunication, SbtShellRunner, SettingQueryHandler}
import org.jetbrains.sbt.{SbtVersion, SbtVersionCapabilities}
import org.junit.Assert.assertNotNull

import java.nio.file.Path
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}

//noinspection ApiStatus
abstract class SettingQueryHandlerTestBase extends SbtProjectStructureImportingLike {

  protected def getRelativeTestProjectPath: String

  override protected def copyTestProjectToTemporaryDir: Boolean = true

  override protected def getTestDataProjectPath: String =
    Path.of(TestUtils.getTestDataPath, getRelativeTestProjectPath).toString

  protected def comm: SbtShellCommunication = myComm
  protected def runner: SbtShellRunner = myRunner

  protected var myComm: SbtShellCommunication = _
  protected var myRunner: SbtShellRunner = _
  protected var logger: ProcessLogger = _

  protected val DefaultCommandWaitTimeout: FiniteDuration = 60.seconds

  private lazy val testProjectUri: String = s"file:${getTestProjectDir.getCanonicalPath}/"
  private lazy val sbtProjectUriAndId = SbtProjectUriAndId(
    uri = testProjectUri,
    id = "scalaTest"
  )

  override protected def setUpFixtures(): Unit = {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory.createFixtureBuilder(getName, getTestProjectPath, useDirectoryBasedStorageFormat()).getFixture
    myTestFixture.setUp()
  }

  override def setUp(): Unit = {
    getCurrentExternalProjectSettings.useSbtShellForImport = true

    super.setUp()

    importProject()

    myComm = SbtShellCommunication.forProject(getProject)
    assertNotNull(myComm)

    myRunner = SbtProcessManager.forProject(getProject).acquireShellRunner()
    assertNotNull(myRunner)

    logger = new ProcessLogger
    myRunner.getProcessHandler.addProcessListener(logger)
  }

  def testFailedCommand(): Unit = {
    Await.result(comm.command("set npSuchSetting:=42"), DefaultCommandWaitTimeout)
    runner.getConsoleView.flushDeferredText()
    assert(logger.getLog.contains(SbtProjectPlatformTestCase.errorPrefix))
  }

  def testShow(): Unit =
    doTestShowSetting(
      commandBefore = SbtSetCommand(SbtSetCommandSettingPath("scalaTest", "fork"), "true"),
      settingName = "fork",
      expectedValue = "true"
    )

  def testSet(): Unit =
    doTestSetSetting(
      settingName = "fork",
      expectedValue = "true"
    )

  def testAdd(): Unit =
    doTestAddToSetting(
      settingName = "javaOptions",
      setCommand = SbtSetCommand(SbtSetCommandSettingPath("scalaTest", "javaOptions"), "List(\"optOne\")"),
      addValue = """"optTwo"""",
      expectedValue = "List(optOne, optTwo)"
    )

  protected def doTestShowSetting(
    commandBefore: SbtSetCommand,
    settingName: String,
    expectedValue: String,
    timeout: Duration = DefaultCommandWaitTimeout,
  ): Unit = {
    val handler = new SettingQueryHandler(Some(sbtProjectUriAndId), settingName, comm)

    val sbtVersion = comm.getRunningOrDetectedSbtVersion
    val sbtCommand = commandBefore.toSbtCommand(sbtVersion)
    val res = Await.result(
      comm.command(sbtCommand).flatMap { _ => handler.getSettingValue },
      timeout
    )
    runner.getConsoleView.flushDeferredText()

    val log = logger.getLog

    assert(res == expectedValue, s"Invalid value read by SettingQueryHandler: '$expectedValue' expected, but '$res' found. Full log:\n$log")
    assert(!logger.getLog.contains(SbtProjectPlatformTestCase.errorPrefix), s"log contained errors. Full log:\n $log")
  }

  protected def doTestSetSetting(
    settingName: String,
    expectedValue: String,
    timeout: Duration = DefaultCommandWaitTimeout,
  ): Unit = {
    val setHandler = new SettingQueryHandler(Some(sbtProjectUriAndId), settingName, comm)
    val handler = new SettingQueryHandler(Some(sbtProjectUriAndId), settingName, comm)

    val res = Await.result(
      setHandler.setSettingValue(expectedValue).flatMap { _ => handler.getSettingValue },
      timeout
    )
    runner.getConsoleView.flushDeferredText()
    val log = logger.getLog
    assert(res == expectedValue, s"Invalid value read by SettingQueryHandler: '$expectedValue' expected, but '$res' found. Full log:\n$log")
    assert(!logger.getLog.contains(SbtProjectPlatformTestCase.errorPrefix), s"log contained errors. Full log:\n $log")
  }

  protected def doTestAddToSetting(
    settingName: String,
    setCommand: SbtSetCommand,
    addValue: String,
    expectedValue: String,
    timeout: Duration = DefaultCommandWaitTimeout,
  ): Unit = {
    val handler = new SettingQueryHandler(Some(sbtProjectUriAndId), settingName, comm)
    val addHandler = new SettingQueryHandler(Some(sbtProjectUriAndId), settingName, comm)

    val setSbtCommandText = setCommand.toSbtCommand(comm.getRunningOrDetectedSbtVersion)
    val res = Await.result(
      for {
        _ <- comm.command(setSbtCommandText)
        _ <- addHandler.addToSettingValue(addValue)
        v <- handler.getSettingValue
      } yield v,
      timeout
    ).trim
    runner.getConsoleView.flushDeferredText()
    val log = logger.getLog
    assert(res == expectedValue, s"Invalid value read by SettingQueryHandler: '$expectedValue' expected, but '$res' found. Full log:\n$log")
    assert(!logger.getLog.contains(SbtProjectPlatformTestCase.errorPrefix), s"log contained errors. Full log:\n $log")
  }
}

object SettingQueryHandlerTestBase {

  case class SbtSetCommandSettingPath(
    projectRefOrId: String,
    settingName: String,
  ) {
    def toSbtCommandForSet(sbtVersion: SbtVersion): String = {
      val useSlash = SbtVersionCapabilities.isSlashSyntaxSupported(sbtVersion)
      val scope = "Test"
      if (useSlash) // set project / task / setting := ...
        s"$projectRefOrId/$scope/$settingName"
      else // set setting.in(project) in task  := ...
        s"$settingName.in($projectRefOrId).in($scope)"
    }
  }

  case class SbtSetCommand(settingPath: SbtSetCommandSettingPath, value: String) {
    def toSbtCommand(sbtVersion: SbtVersion): String =
      s"set ${settingPath.toSbtCommandForSet(sbtVersion)} := $value"
  }
}