package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.ExpectedFolding
import org.jetbrains.plugins.scala.worksheet.integration.{WorksheetIntegrationBaseTest, WorksheetRunTestSettings}
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType
import org.junit.experimental.categories.Category

import scala.language.postfixOps

@Category(Array(classOf[SlowTests]))
class WorksheetReplIntegrationTest extends WorksheetIntegrationBaseTest with WorksheetRunTestSettings {

  override def runType: WorksheetExternalRunType = WorksheetExternalRunType.ReplRunType

  override def compileInCompileServerProcess: Boolean = true

  override def runInCompileServerProcess: Boolean = true

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
