package org.jetbrains.plugins.scala.codeInspection.cast

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestBase

class RedundantConversionInspectionTest extends ScalaQuickFixTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaRedundantConversionInspection]
  override protected val description = "Casting '<from>' to '<to>' is redundant"

  override protected def descriptionMatches(s: String): Boolean = s != null && s.startsWith("Casting '")

  def test_int(): Unit = {
    checkTextHasError(s"val x = 3$START.toInt$END")
    testQuickFix(
      "val x = 3.toInt",
      "val x = 3",
      "Remove Redundant Conversion"
    )
  }

  def test_string(): Unit = {
    checkTextHasError(s"""val x = ""$START.toString$END""")
    testQuickFix(
      """val x = "".toString """,
      """val x = "" """,
      "Remove Redundant Conversion"
    )
  }

  //SCL-17290
  def test_toString_on_variable(): Unit = {
    checkTextHasError(
      s"""
         |val x = ""
         |val y = x$START.toString$END
         |""".stripMargin)
    testQuickFix(
      s"""
         |val x = ""
         |val y = x.toString
         |""".stripMargin,
      s"""
         |val x = ""
         |val y = x
         |""".stripMargin,
      "Remove Redundant Conversion"
    )
  }


  val tryDef =
    """
      |class Try[+T](value: T) {
      |  def fold[U](f1: Any => U, f2: T => U): U = f1(())
      |}
      |def Try[T](a: T): Try[T] = new Try(a)
      |
      |""".stripMargin

  def test_SLC16197(): Unit = {
    checkTextHasError(tryDef + s"""val x: String = Try("Hello").fold(_.toString, _$START.toString$END)""")
    testQuickFix(
      tryDef + """val x: String = Try("Hello").fold(_.toString, _.toString)""",
      tryDef + """val x: String = Try("Hello").fold(_.toString, identity)""",
      "Remove Redundant Conversion"
    )
  }
  def test_SLC16197_neg(): Unit = {

    checkTextHasError(tryDef + s"""val x: String = Try("Hello").fold(_.toString, _$START.toString$END + 3)""")
    testQuickFix(
      tryDef + """val x: String = Try("Hello").fold(_.toString, _.toString + 3)""",
      tryDef + """val x: String = Try("Hello").fold(_.toString, _ + 3)""",
      "Remove Redundant Conversion"
    )
  }

  def test_toString_removal(): Unit = testQuickFix(
    """
      |def test(arg: String, i: Int) = ()
      |test("".toString, 3)
      |""".stripMargin,
    """
      |def test(arg: String, i: Int) = ()
      |test("", 3)
      |""".stripMargin,
    "Remove Redundant Conversion"
  )
}
