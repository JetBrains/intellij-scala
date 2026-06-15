package org.jetbrains.sbt.shell.testSettingsQueryHandler

import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.build.BuildMessages
import org.jetbrains.sbt.SbtUtil.SbtProjectUriAndId
import org.jetbrains.sbt.shell.testSettingsQueryHandler.SettingQueryHandlerTestBase.{SbtSetCommand, SbtSetCommandSettingPath}
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtRuntimeTest_WithSbtShell, SbtShellTestUtil, SettingQueryHandler}
import org.jetbrains.sbt.{SbtVersion, SbtVersionCapabilities}
import org.junit.experimental.categories.Category

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

@Category(Array(classOf[SlowTests2]))
//noinspection ApiStatus
abstract class SettingQueryHandlerTestBase extends SbtRuntimeTest_WithSbtShell {

  private lazy val sbtProjectUriAndId = SbtProjectUriAndId(
    uri = getTestProjectPath.toUri.toString,
    id = "scalaTest"
  )

  def testFailedCommand(): Unit = {
    SbtShellTestUtil.awaitFutureWithShellLog(
      comm.runAndCollectOutput("set npSuchSetting:=42"),
      DefaultCommandWaitTimeout,
      "running failing sbt command `set npSuchSetting:=42`",
      processListener
    )
    flush()
    val logNoAnsi = BuildMessages.stripAnsiCodes(processListener.getLog)
    assert(logNoAnsi.contains(SbtShellTestUtil.ErrorPrefix))
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
    timeout: FiniteDuration = DefaultCommandWaitTimeout,
  ): Unit = {
    val handler = new SettingQueryHandler(Some(sbtProjectUriAndId), settingName, comm)

    val sbtVersion = comm.getRunningOrDetectedSbtVersion
    val sbtCommand = commandBefore.toSbtCommand(sbtVersion)
    val res = SbtShellTestUtil.awaitFutureWithShellLog(
      comm.runAndCollectOutput(sbtCommand).flatMap { _ => handler.getSettingValue },
      timeout,
      s"reading sbt setting '$settingName' after running `$sbtCommand`",
      processListener
    )
    flush()

    val log = processListener.getLog

    assert(res == expectedValue, s"Invalid value read by SettingQueryHandler: '$expectedValue' expected, but '$res' found. Full log:\n$log")
    assert(!processListener.getLog.contains(SbtShellTestUtil.ErrorPrefix), s"log contained errors. Full log:\n $log")
  }

  protected def doTestSetSetting(
    settingName: String,
    expectedValue: String,
    timeout: FiniteDuration = DefaultCommandWaitTimeout,
  ): Unit = {
    val setHandler = new SettingQueryHandler(Some(sbtProjectUriAndId), settingName, comm)
    val handler = new SettingQueryHandler(Some(sbtProjectUriAndId), settingName, comm)

    val res = SbtShellTestUtil.awaitFutureWithShellLog(
      setHandler.setSettingValue(expectedValue).flatMap { _ => handler.getSettingValue },
      timeout,
      s"setting sbt setting '$settingName' to '$expectedValue' and reading it back",
      processListener
    )
    flush()
    val log = processListener.getLog
    assert(res == expectedValue, s"Invalid value read by SettingQueryHandler: '$expectedValue' expected, but '$res' found. Full log:\n$log")
    assert(!processListener.getLog.contains(SbtShellTestUtil.ErrorPrefix), s"log contained errors. Full log:\n $log")
  }

  protected def doTestAddToSetting(
    settingName: String,
    setCommand: SbtSetCommand,
    addValue: String,
    expectedValue: String,
    timeout: FiniteDuration = DefaultCommandWaitTimeout,
  ): Unit = {
    val handler = new SettingQueryHandler(Some(sbtProjectUriAndId), settingName, comm)
    val addHandler = new SettingQueryHandler(Some(sbtProjectUriAndId), settingName, comm)

    val setSbtCommandText = setCommand.toSbtCommand(comm.getRunningOrDetectedSbtVersion)
    val res = SbtShellTestUtil.awaitFutureWithShellLog(
      for {
        _ <- comm.runAndCollectOutput(setSbtCommandText)
        _ <- addHandler.addToSettingValue(addValue)
        v <- handler.getSettingValue
      } yield v,
      timeout,
      s"adding '$addValue' to sbt setting '$settingName' after running `$setSbtCommandText`",
      processListener
    ).trim
    flush()
    val log = processListener.getLog
    assert(res == expectedValue, s"Invalid value read by SettingQueryHandler: '$expectedValue' expected, but '$res' found. Full log:\n$log")
    assert(!processListener.getLog.contains(SbtShellTestUtil.ErrorPrefix), s"log contained errors. Full log:\n $log")
  }

  private def flush(): Unit = {
    SbtProcessManager.forProject(getMyProject).flushConsoleOutputForTests()
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
