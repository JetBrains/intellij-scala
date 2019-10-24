package org.jetbrains.plugins.scala.worksheet.integration.plain

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.ExpectedFolding
import org.jetbrains.plugins.scala.worksheet.settings.{WorksheetCommonSettings, WorksheetExternalRunType}
import org.junit.experimental.categories.Category

import scala.language.postfixOps

@Category(Array(classOf[SlowTests]))
abstract class WorksheetPlainIntegrationBaseTest extends WorksheetIntegrationBaseTest {

  override def setupWorksheetSettings(settings: WorksheetCommonSettings): Unit = {
    settings.setRunType(WorksheetExternalRunType.PlainRunType)
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
        |b: Int = 2
        |""".stripMargin

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
        |res0: Unit = ()
        |x: Int = 42
        |""".stripMargin

    val foldings = Seq(ExpectedFolding(0, 21, Some("1\n2\n3\nres0: Unit = ()")))

    doTest(left, right, foldings)
  }
}
