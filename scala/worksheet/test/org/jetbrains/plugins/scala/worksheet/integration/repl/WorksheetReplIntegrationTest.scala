package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.ExpectedFolding
import org.jetbrains.plugins.scala.worksheet.settings.{WorksheetCommonSettings, WorksheetExternalRunType}
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_12, SlowTests}
import org.junit.experimental.categories.Category

import scala.language.postfixOps

@Category(Array(classOf[SlowTests]))
class WorksheetReplIntegrationTest extends WorksheetIntegrationBaseTest {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala_2_12

  override protected def useCompileServer: Boolean = true

  override def setupWorksheetSettings(settings: WorksheetCommonSettings): Unit = {
    settings.setRunType(WorksheetExternalRunType.ReplRunType)
    settings.setInteractive(false)
    settings.setMakeBeforeRun(false)
  }

  def testSimpleDeclaration(): Unit = {
    val left =
      """val a = 1
        |val b = 2
        |""".stripMargin

    val right =
      """a: Int = 1
        |b: Int = 2""".stripMargin

    doTest(left, right)
  }

  def testSimpleFolding(): Unit = {
    val left =
      """println("1\n2\n3")
        |val x = 42
        |""".stripMargin

    val right =
      """1
        |2
        |3
        |x: Int = 42""".stripMargin

    val foldings = Seq(ExpectedFolding(0, 5, Some("1\n2\n3")))

    doTest(left, right, foldings)
  }
}
