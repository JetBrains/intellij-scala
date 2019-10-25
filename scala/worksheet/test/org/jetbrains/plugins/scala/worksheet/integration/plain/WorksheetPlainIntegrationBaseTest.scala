package org.jetbrains.plugins.scala.worksheet.integration.plain

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.worksheet.integration.{WorksheetIntegrationBaseTest, WorksheetRunTestSettings}
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType
import org.junit.experimental.categories.Category

import scala.language.postfixOps

@Category(Array(classOf[SlowTests]))
abstract class WorksheetPlainIntegrationBaseTest extends WorksheetIntegrationBaseTest with WorksheetRunTestSettings {

  override def runType: WorksheetExternalRunType = WorksheetExternalRunType.PlainRunType

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
      s"""${start}1
         |2
         |3
         |res0: Unit = ()$end
         |x: Int = 42
         |""".stripMargin

    doTest(left, right)
  }

  def testMultipleFoldings(): Unit = {
    val left =
      """println("1\n2\n3")
        |val x = 42
        |println("4\n5\n6")
        |val y = 23
        |""".stripMargin

    val right =
      s"""${start}1
         |2
         |3
         |res0: Unit = ()$end
         |x: Int = 42
         |${start}4
         |5
         |6
         |res1: Unit = ()$end
         |y: Int = 23
         |""".stripMargin

    doTest(left, right)
  }
}
