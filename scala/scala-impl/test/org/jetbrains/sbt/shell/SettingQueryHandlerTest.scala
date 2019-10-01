package org.jetbrains.sbt.shell

import java.util.concurrent.TimeUnit

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

/**
  * Created by Roman.Shein on 27.03.2017.
  */
@Category(Array(classOf[PerfCycleTests]))
abstract class SettingQueryHandlerTest extends SbtProjectPlatformTestCase {

  def testFailedCommand(): Unit = {
    Await.result(comm.command("set npSuchSetting:=42", showShell = false), Duration(timeout, "second"))
    runner.getConsoleView.flushDeferredText()
    assert(logger.getLog.contains(SbtProjectPlatformTestCase.errorPrefix))
  }

  def testProjectShow(): Unit =
    doTestShowSetting("set fork in scalaTest in Test:=true", "fork", "true", timeout, Some("test"),
      projectName = Some("scalaTest"))

  def testProjectNoTaskShow(): Unit =
    doTestShowSetting("set fork in scalaTest:=true", "fork", "true", timeout, projectName = Some("scalaTest"))

  def testProjectWithUriShow(): Unit =
    doTestShowSetting("set fork in scalaTest:=true", "fork", "true", timeout, projectName = Some("scalaTest"),
      projectUri = Some(getScalaTestProjectUri))

  def testProjectSet(): Unit =
    doTestSetSetting("fork", "true", timeout, Some("Test"), Some("test"), projectName = Some("scalaTest"))

  def testProjectWithUriSet(): Unit =
    doTestSetSetting("fork", "true", timeout, Some("Test"), Some("test"), Some(getScalaTestProjectUri), Some("scalaTest"))

  def testProjectNoTaskSet(): Unit =
    doTestSetSetting("fork", "true", timeout, projectName = Some("scalaTest"))

  def testProjectNoTaskAdd(): Unit =
    doTestAddToSetting("javaOptions", """set javaOptions in scalaTest:=List("optOne")""", """"optTwo"""",
      "List(optOne, optTwo)", timeout, projectName = Some("scalaTest"))

  def testProjectAdd(): Unit =
    doTestAddToSetting("javaOptions", """set javaOptions in scalaTest in Test:=List("optOne")""", """"optTwo"""",
      "List(optOne, optTwo)", timeout, Some("Test"), Some("test"), projectName = Some("scalaTest"))

  def testProjectWithUriAdd(): Unit =
    doTestAddToSetting("javaOptions", """set javaOptions in scalaTest in Test:=List("optOne")""", """"optTwo"""",
      "List(optOne, optTwo)", timeout, Some("Test"), Some("test"), Some(getScalaTestProjectUri), Some("scalaTest"))


  protected def doTestShowSetting(commandBefore: String, settingName: String, expectedValue: String, timeoutSeconds: Int,
                                  taskName: Option[String] = None, projectUri: Option[String]= None,
                                  projectName: Option[String] = None): Unit = {
    val handler = SettingQueryHandler(settingName, taskName, projectUri, projectName, comm)
    val res = Await.result(
      comm.command(commandBefore, showShell = false).flatMap { _ => handler.getSettingValue() },
      Duration(timeoutSeconds, TimeUnit.SECONDS))
    runner.getConsoleView.flushDeferredText()
    val log = logger.getLog
    assert(res == expectedValue, s"Invalid value read by SettingQueryHandler: '$expectedValue' expected, but '$res' found. Full log:\n$log")
    assert(!logger.getLog.contains(SbtProjectPlatformTestCase.errorPrefix), s"log contained errors. Full log:\n $log")
  }

  protected def doTestSetSetting(settingName: String, expectedValue: String, timeoutSeconds: Int,
                                 setTaskName: Option[String] = None, taskName: Option[String] = None,
                                 projectUri: Option[String] = None, projectName: Option[String] = None): Unit = {
    val setHandler = SettingQueryHandler(settingName, setTaskName, projectUri, projectName, comm)
    val handler = SettingQueryHandler(settingName, taskName, projectUri, projectName, comm)
    val res = Await.result(setHandler.setSettingValue(expectedValue).flatMap { _ => handler.getSettingValue() },
      Duration(timeoutSeconds, "second"))
    runner.getConsoleView.flushDeferredText()
    val log = logger.getLog
    assert(res == expectedValue, s"Invalid value read by SettingQueryHandler: '$expectedValue' expected, but '$res' found. Full log:\n$log")
    assert(!logger.getLog.contains(SbtProjectPlatformTestCase.errorPrefix), s"log contained errors. Full log:\n $log")
  }

  protected def doTestAddToSetting(settingName: String, setCommand: String, addValue: String, expectedValue: String,
                                   timeoutSeconds: Int, addTaskName: Option[String] = None,
                                   taskName: Option[String] = None, projectUri: Option[String] = None,
                                   projectName: Option[String] = None): Unit = {
    val addHandler = SettingQueryHandler(settingName, addTaskName, projectUri, projectName, comm)
    val handler = SettingQueryHandler(settingName, taskName, projectUri, projectName, comm)
    val res = Await.result(comm.command(setCommand, showShell = false).flatMap {
      _ => addHandler.addToSettingValue(addValue)
    }.flatMap {
      _ => handler.getSettingValue()
    }, Duration(timeoutSeconds, "second")).trim
    runner.getConsoleView.flushDeferredText()
    val log = logger.getLog
    assert(res == expectedValue, s"Invalid value read by SettingQueryHandler: '$expectedValue' expected, but '$res' found. Full log:\n$log")
    assert(!logger.getLog.contains(SbtProjectPlatformTestCase.errorPrefix), s"log contained errors. Full log:\n $log")
  }

  protected def getScalaTestProjectUri: String = "file:" + getBasePath.replace("\\", "/") + "/" + getPath + "/"

  protected val timeout = 60
}
