package org.jetbrains.plugins.scala.codeInspection.parameters

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

// TODO constructors
class RedundantDefaultArgumentInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[RedundantDefaultArgumentInspection]

  override protected val description = "Argument duplicates corresponding parameter default value"

  private val hint = "Delete redundant default argument"

  private def testFix(text: String, result: String): Unit = testQuickFix(text, result, hint)

  def test_Simple(): Unit = {
    val selectedText =
      s"""
         |def f(x: Int = 0) {}
         |f(${START}0$END)
       """.stripMargin
    checkTextHasError(selectedText)

    val text =
      """
        |def f(x: Int = 0) {}
        |f(0)
      """.stripMargin
    val result =
      """
        |def f(x: Int = 0) {}
        |f()
      """.stripMargin

    testFix(text, result)
  }

  def test_SimpleNotDefault(): Unit = {
    val text =
      """
        |def f(x: Int) {}
        |f(0)
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def test_SimpleWrongValue(): Unit = {
    val text =
      """
        |def f(x: Int = 0) {}
        |f(1)
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def test_NamedArgument(): Unit = {
    val selectedText =
      s"""
         |def f(x: Int, y: Int = 0, z: Int) {}
         |f(1, ${START}y=0$END, z=1)
       """.stripMargin
    checkTextHasError(selectedText)

    val text =
      """
        |def f(x: Int, y: Int = 0, z: Int) {}
        |f(1, y=0, z=1)
      """.stripMargin

    val result =
      """
        |def f(x: Int, y: Int = 0, z: Int) {}
        |f(1, z=1)
      """.stripMargin
    testFix(text, result)
  }

  def test_NotLastArgument(): Unit = {
    val text =
      """
        |def f(x: Int, y: Int = 0, z: Int) {]
        |f(1, 0, 1)
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def test_ArgumentAfterNamed(): Unit = {
    val text =
      """
        |def f(x: Int, y: Int = 0, z: Int) {}
        |f(1, y=0, 1)
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def test_LastArgument(): Unit = {
    val selectedText =
      s"""
         |def f(x: Int, y: Int = 0) {}
         |f(1, ${START}0$END)
      """.stripMargin
    checkTextHasError(selectedText)

    val text =
      """
        |def f(x: Int, y: Int = 0) {}
        |f(1, 0)
      """.stripMargin

    val result =
      """
        |def f(x: Int, y: Int = 0) {}
        |f(1)
      """.stripMargin
    testFix(text, result)
  }

  def test_LastArgumentBeforeNamed(): Unit = {
    val selectedText =
      s"""
         |def f(x: Int, y: Int = 0, z: Int, t: Int) {}
         |f(1, ${START}0$END, z = 1, t = 2)
      """.stripMargin
    checkTextHasError(selectedText)

    val text =
      """
        |def f(x: Int, y: Int = 0, z: Int, t: Int) {}
        |f(1, 0, z = 1, t = 2)
      """.stripMargin

    val result =
      """
        |def f(x: Int, y: Int = 0, z: Int, t: Int) {}
        |f(1, z = 1, t = 2)
      """.stripMargin
    testFix(text, result)
  }

  def test_FunctionDeclaration(): Unit = {
    val selectedText =
      s"""
         |def f(x: Int = 0)
         |f(${START}0$END)
       """.stripMargin
    checkTextHasError(selectedText)

    val text =
      """
        |def f(x: Int = 0)
        |f(0)
      """.stripMargin
    val result =
      """
        |def f(x: Int = 0)
        |f()
      """.stripMargin

    testFix(text, result)
  }

  def test_EmptySignature(): Unit = {
    val text =
      """
        |def f()
        |f(1)
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def test_AssignmentNotNamedArg(): Unit = {
    val text =
      """
        |def f(x: Int = 1, y: Unit) = {}
        |var z = 2
        |f(1, z = 3)
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  //currently we cannot compare values of interpolated strings
  def testInterpolatedString(): Unit = {
    val text =
      """
        |  val x = "x"
        |  def foo(s: String = s"x$x")
        |
        |  foo(s"x$x")
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testInterpolatedStringArg(): Unit = {
    checkTextHasNoErrors(
      """def foo(s: String = "")
        |foo(s"aa")
      """.stripMargin)
  }

  def testNamedArgInterpolatedString(): Unit = {
    checkTextHasNoErrors(
      """def foo(s: String = "")
        |foo(s = s"aa")
      """.stripMargin)
  }
}
