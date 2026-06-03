package org.jetbrains.plugins.scala.codeInsight.inspection

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.codeInspection.recursion.NoTailRecursionAnnotationInspection

class NoTailrecAnnotationInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection = classOf[NoTailRecursionAnnotationInspection]
  override protected val description = "No tail recursion annotation"
  private val hint = "Add @tailrec annotation"

  def testAlreadyImported(): Unit = testQuickFix(
    text =
      s"""import scala.annotation._
         |object Test {
         |  def ${START}test$END(i: Int): Int = test(i - 1)
         |}""".stripMargin,

    expected =
      """
        |import scala.annotation._
        |object Test {
        |  @tailrec
        |  def test(i: Int): Int = test(i - 1)
        |}""".stripMargin,
    hint
  )

  def testNeedImport(): Unit = testQuickFix(
    text =
      s"""object Test {
         |  def ${START}test$END(i: Int): Int = test(i - 1)
         |}""".stripMargin,

    expected =
      """import scala.annotation.tailrec
        |
        |object Test {
        |  @tailrec
        |  def test(i: Int): Int = test(i - 1)
        |}""".stripMargin,
    hint
  )

}
