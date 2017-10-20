package org.jetbrains.plugins.scala.codeInspection.parameters

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestBase

class RedundantDefaultArgumentInspectionTest extends ScalaQuickFixTestBase {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[RedundantDefaultArgumentInspection]

  override protected val description = "Argument duplicates corresponding parameter default value"

  private val hint = "Delete redundant default argument"

  private def testFix(text: String, result: String): Unit = testQuickFix(text, result, hint)

  def test_Simple() {
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

  def test_SimpleNotDefault() {
    val text =
      """
        |def f(x: Int) {}
        |f(0)
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def test_SimpleWrongValue() {
    val text =
      """
        |def f(x: Int = 0) {}
        |f(1)
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def test_NamedArgument() {
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

  def test_NotLastArgument() {
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

  def test_LastArgument() {
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

  def test_LastArgumentBeforeNamed() {
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

  def test_FunctionDeclaration() {
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
