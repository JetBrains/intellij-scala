package org.jetbrains.sbt.shell

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

/**
  * Created by Roman.Shein on 27.03.2017.
  */
class SettingQueryHandlerTest extends SbtProjectPlatformTestCase {

  def testFailedCommand() = {
    Await.result(comm.command("set npSuchSetting:=42", showShell = false), Duration(10, "second"))
    runner.getConsoleView.flushDeferredText()
    assert(logger.getLog.contains(SettingQueryHandlerTest.errorPrefix))
  }

  def testProjectShow() =
    doTestShowSetting("set fork in scalaTest in Test:=true", "fork", "true", 30, Some("test"), projectName = Some("scalaTest"))

  def testProjectNoTaskShow() =
    doTestShowSetting("set fork in scalaTest:=true", "fork", "true", 30, projectName = Some("scalaTest"))

  def testProjectWithUriShow() =
    doTestShowSetting("set fork in scalaTest:=true", "fork", "true", 30, projectName = Some("scalaTest"),
      projectUri = Some(getScalaTestProjectUri))

  def testProjectSet() =
    doTestSetSetting("fork", "true", 30, Some("Test"), Some("test"), projectName = Some("scalaTest"))

  def testProjectWithUriSet() =
    doTestSetSetting("fork", "true", 30, Some("Test"), Some("test"), Some(getScalaTestProjectUri), Some("scalaTest"))

  def testProjectNoTaskSet() =
    doTestSetSetting("fork", "true", 30, projectName = Some("scalaTest"))

  def testProjectNoTaskAdd() =
    doTestAddToSetting("javaOptions", "set javaOptions in scalaTest:=List(\"optOne\")", "\"optTwo\"", "List(optOne, optTwo)", 30,
      projectName = Some("scalaTest"))

  def testProjectAdd() =
    doTestAddToSetting("javaOptions", "set javaOptions in scalaTest in Test:=List(\"optOne\")", "\"optTwo\"",
      "List(optOne, optTwo)", 30, Some("Test"), Some("test"), projectName = Some("scalaTest"))

  def testProjectWithUriAdd() =
    doTestAddToSetting("javaOptions", "set javaOptions in scalaTest in Test:=List(\"optOne\")", "\"optTwo\"",
      "List(optOne, optTwo)", 30, Some("Test"), Some("test"), Some(getScalaTestProjectUri), Some("scalaTest"))

  override def getPath: String = "sbt/shell/settingQueryHandlerTest"

  protected def doTestShowSetting(commandBefore: String, settingName: String, expectedValue: String, timeoutSeconds: Int,
                                  taskName: Option[String] = None, projectUri: Option[String]= None,
                                  projectName: Option[String] = None) = {
    val handler = SettingQueryHandler(settingName, taskName, projectUri, projectName, comm)
    val res = Await.result(comm.command(commandBefore, showShell = false).flatMap { _ => handler.getSettingValue() },
      Duration(timeoutSeconds, "second"))
    runner.getConsoleView.flushDeferredText()
    assert(res == expectedValue, s"Invalid value read by SettingQueryHandler: '$expectedValue' expected, but '$res' found")
    assert(!logger.getLog.contains(SettingQueryHandlerTest.errorPrefix))
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
    assert(!logger.getLog.contains(SettingQueryHandlerTest.errorPrefix))
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
    }, Duration(timeoutSeconds, "second"))
    runner.getConsoleView.flushDeferredText()
    assert(res == expectedValue, s"Invalid value read by SettingQueryHandler: '$expectedValue' expected, but '$res' found")
    assert(!logger.getLog.contains(SettingQueryHandlerTest.errorPrefix))
  }

  protected def getScalaTestProjectUri: String = "file:/" + getBasePath.replace("\\", "/") + "/" + getPath + "/"
}

object SettingQueryHandlerTest {
  val errorPrefix = "[error]"
}