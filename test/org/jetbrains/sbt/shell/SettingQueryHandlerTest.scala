package org.jetbrains.sbt.shell

import java.util.concurrent.TimeUnit

import org.jetbrains.plugins.scala.SlowTests
import org.junit.experimental.categories.Category

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

/**
  * Created by Roman.Shein on 27.03.2017.
  */
@Category(Array(classOf[SlowTests]))
abstract class SettingQueryHandlerTest extends SbtProjectPlatformTestCase {

  def testFailedCommand() = {
    Await.result(comm.command("set npSuchSetting:=42", showShell = false), Duration(timeout, "second"))
    runner.getConsoleView.flushDeferredText()
    assert(logger.getLog.contains(SbtProjectPlatformTestCase.errorPrefix))
  }

  def testProjectShow() =
    doTestShowSetting("set fork in scalaTest in Test:=true", "fork", "true", timeout, Some("test"),
      projectName = Some("scalaTest"))

  def testProjectNoTaskShow() =
    doTestShowSetting("set fork in scalaTest:=true", "fork", "true", timeout, projectName = Some("scalaTest"))

  def testProjectWithUriShow() =
    doTestShowSetting("set fork in scalaTest:=true", "fork", "true", timeout, projectName = Some("scalaTest"),
      projectUri = Some(getScalaTestProjectUri))

  def testProjectSet() =
    doTestSetSetting("fork", "true", timeout, Some("Test"), Some("test"), projectName = Some("scalaTest"))

  def testProjectWithUriSet() =
    doTestSetSetting("fork", "true", timeout, Some("Test"), Some("test"), Some(getScalaTestProjectUri), Some("scalaTest"))

  def testProjectNoTaskSet() =
    doTestSetSetting("fork", "true", timeout, projectName = Some("scalaTest"))

  def testProjectNoTaskAdd() =
    doTestAddToSetting("javaOptions", """set javaOptions in scalaTest:=List("optOne")""", """"optTwo"""",
      "List(optOne, optTwo)", timeout, projectName = Some("scalaTest"))

  def testProjectAdd() =
    doTestAddToSetting("javaOptions", """set javaOptions in scalaTest in Test:=List("optOne")""", """"optTwo"""",
      "List(optOne, optTwo)", timeout, Some("Test"), Some("test"), projectName = Some("scalaTest"))

  def testProjectWithUriAdd() =
    doTestAddToSetting("javaOptions", """set javaOptions in scalaTest in Test:=List("optOne")""", """"optTwo"""",
      "List(optOne, optTwo)", timeout, Some("Test"), Some("test"), Some(getScalaTestProjectUri), Some("scalaTest"))


  protected def doTestShowSetting(commandBefore: String, settingName: String, expectedValue: String, timeoutSeconds: Int,
                                  taskName: Option[String] = None, projectUri: Option[String]= None,
                                  projectName: Option[String] = None) = {
    val handler = SettingQueryHandler(settingName, taskName, projectUri, projectName, comm)
    val res = Await.result(
      comm.command(commandBefore, showShell = false).flatMap { _ => handler.getSettingValue() },
      Duration(timeoutSeconds, TimeUnit.SECONDS))
    runner.getConsoleView.flushDeferredText()
    assert(res == expectedValue, s"Invalid value read by SettingQueryHandler: '$expectedValue' expected, but '$res' found")
    assert(!logger.getLog.contains(SbtProjectPlatformTestCase.errorPrefix))
  }

  protected def doTestSetSetting(settingName: String, expectedValue: String, timeoutSeconds: Int,
                                 setTaskName: Option[String] = None, taskName: Option[String] = None,
                                 projectUri: Option[String] = None, projectName: Option[String] = None) = {
    val setHandler = SettingQueryHandler(settingName, setTaskName, projectUri, projectName, comm)
    val handler = SettingQueryHandler(settingName, taskName, projectUri, projectName, comm)
    val res = Await.result(setHandler.setSettingValue(expectedValue).flatMap { _ => handler.getSettingValue() },
      Duration(timeoutSeconds, "second"))
    runner.getConsoleView.flushDeferredText()
    assert(res == expectedValue, s"Invalid value read by SettingQueryHandler: '$expectedValue' expected, but '$res' found")
    assert(!logger.getLog.contains(SbtProjectPlatformTestCase.errorPrefix))
  }

  protected def doTestAddToSetting(settingName: String, setCommand: String, addValue: String, expectedValue: String,
                                   timeoutSeconds: Int, addTaskName: Option[String] = None,
                                   taskName: Option[String] = None, projectUri: Option[String] = None,
                                   projectName: Option[String] = None) = {
    val addHandler = SettingQueryHandler(settingName, addTaskName, projectUri, projectName, comm)
    val handler = SettingQueryHandler(settingName, taskName, projectUri, projectName, comm)
    val res = Await.result(comm.command(setCommand, showShell = false).flatMap {
      _ => addHandler.addToSettingValue(addValue)
    }.flatMap {
      _ => handler.getSettingValue()
    }, Duration(timeoutSeconds, "second")).trim
    runner.getConsoleView.flushDeferredText()
    assert(res == expectedValue, s"Invalid value read by SettingQueryHandler: '$expectedValue' expected, but '$res' found")
    assert(!logger.getLog.contains(SbtProjectPlatformTestCase.errorPrefix))
  }

  protected def getScalaTestProjectUri: String = "file:" + getBasePath.replace("\\", "/") + "/" + getPath + "/"

  protected val timeout = 60
}