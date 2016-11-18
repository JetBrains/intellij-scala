package org.jetbrains.plugins.scala.codeInspection.parameters

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class RedundantDefaultArgumentInspectionTest extends ScalaLightCodeInsightFixtureTestAdapter {
  val s = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_START
  val e = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_END
  val annotation = "Argument duplicates corresponding parameter default value"
  val quickFixHint = "Delete redundant default argument"

  private def check(text: String) {
    checkTextHasError(text, annotation, classOf[RedundantDefaultArgumentInspection])
  }

  private def testFix(text: String, result: String) {
    testQuickFix(text.replace("\r", ""), result.replace("\r", ""), quickFixHint, classOf[RedundantDefaultArgumentInspection])
  }

  private def checkHasNoErrors(text: String) {
    checkTextHasNoErrors(text, annotation, classOf[RedundantDefaultArgumentInspection])
  }

  def test_Simple() {
    val selectedText =
      s"""
         |def f(x: Int = 0) {}
         |f(${s}0$e)
       """.stripMargin
    check(selectedText)

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
    checkHasNoErrors(text)
  }

  def test_SimpleWrongValue() {
    val text =
      """
        |def f(x: Int = 0) {}
        |f(1)
      """.stripMargin
    checkHasNoErrors(text)
  }

  def test_NamedArgument() {
    val selectedText =
      s"""
         |def f(x: Int, y: Int = 0, z: Int) {}
         |f(1, ${s}y=0$e, z=1)
       """.stripMargin
    check(selectedText)

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
    checkHasNoErrors(text)
  }

  def test_ArgumentAfterNamed(): Unit = {
    val text =
      """
        |def f(x: Int, y: Int = 0, z: Int) {}
        |f(1, y=0, 1)
      """.stripMargin
    checkHasNoErrors(text)
  }

  def test_LastArgument() {
    val selectedText =
      s"""
         |def f(x: Int, y: Int = 0) {}
         |f(1, ${s}0$e)
      """.stripMargin
    check(selectedText)

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
         |f(1, ${s}0$e, z = 1, t = 2)
      """.stripMargin
    check(selectedText)

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
         |f(${s}0$e)
       """.stripMargin
    check(selectedText)

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

  def test_EmptySugnature(): Unit = {
    val text =
      """
        |def f()
        |f(1)
      """.stripMargin
    checkHasNoErrors(text)
  }

  def test_AssignmentNotNamedArg(): Unit = {
    val text =
      """
        |def f(x: Int = 1, y: Unit) = {}
        |var z = 2
        |f(1, z = 3)
      """.stripMargin
    checkHasNoErrors(text)
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
    checkHasNoErrors(text)
  }

  def testInterpolatedStringArg(): Unit = {
    checkHasNoErrors(
      """def foo(s: String = "")
        |foo(s"aa")
      """.stripMargin)
  }
}
