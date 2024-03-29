package org.jetbrains.plugins.scala.worksheet.integration.plain.eclipse_mode

import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.runners.{RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}
import org.jetbrains.plugins.scala.worksheet.integration.{WorksheetIntegrationBaseTest, WorksheetRunTestSettings}
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType

import scala.language.postfixOps

class WorksheetEclipseModeIntegrationBaseTest extends WorksheetIntegrationBaseTest with WorksheetRunTestSettings {

  override def runType: WorksheetExternalRunType = WorksheetExternalRunType.PlainRunType

  override def useCompileServer = true

  override def runInCompileServerProcess = true

  override def setUp(): Unit = {
    super.setUp()
    ScalaProjectSettings.getInstance(getProject).setUseEclipseCompatibility(true)
  }

  def testAllInOne(): Unit = {
    val left =
      """import math.abs
        |
        |object session1 {
        |
        |
        |  val value1 = 1
        |  val value2 = 1
        |  //comment1
        |  val value3 = 1
        |
        |
        |  //comment2
        |  val value4 = 1
        |
        |  /*
        |   comment3
        |   */
        |  val value5 = 1
        |
        |
        |  /**
        |   comment5
        |   */
        |  val value6 = 1
        |
        |  def function1(x: Int) =
        |    x + 1
        |
        |  def function2(y: Int) = y + 1
        |
        |  println("1\n2\n3")
        |
        |  println("4\n5\n6\n\n")
        |
        |
        |
        |  println("hello" +
        |    "world" +
        |    "!")
        |}""".stripMargin

    val right =
      s"""import scala.math.abs
         |
         |
         |
         |
         |value1: Int = 1
         |value2: Int = 1
         |
         |value3: Int = 1
         |
         |
         |
         |value4: Int = 1
         |
         |
         |
         |
         |value5: Int = 1
         |
         |
         |
         |
         |
         |value6: Int = 1
         |
         |function1: function1[](val x: Int) => Int
         |
         |
         |function2: function2[](val y: Int) => Int
         |
         |${foldStart}1
         |2
         |3
         |res0: Unit = ()$foldEnd
         |
         |${foldStart}4
         |5
         |6
         |
         |
         |res1: Unit = ()$foldEnd
         |
         |
         |
         |helloworld!
         |res2: Unit = ()
         |""".stripMargin

    doRenderTest(left, right)
  }

  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_2_11_0,
    TestScalaVersion.Scala_2_12_0,
    TestScalaVersion.Scala_2_13_0
  ))
  @RunWithJdkVersions(Array(TestJdkVersion.JDK_11))
  def testAllInOne_OldScalaVersions(): Unit =
    testAllInOne()
}
