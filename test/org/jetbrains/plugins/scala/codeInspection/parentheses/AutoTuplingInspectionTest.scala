package org.jetbrains.plugins.scala.codeInspection.parentheses

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaLightInspectionFixtureTestAdapter
import org.jetbrains.plugins.scala.codeInspection.parameters.{AutoTuplingInspection, MakeTuplesExplicitFix}

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 10/14/15.
  */
class AutoTuplingInspectionTest extends ScalaLightInspectionFixtureTestAdapter {
  override protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[AutoTuplingInspection]

  override protected def annotation: String = AutoTuplingInspection.message
  val hint = MakeTuplesExplicitFix.hint

  def testBasic(): Unit = {
    val text =
      s"""
        |def foo(a: Any) = {}
        |foo$START(1, 2)$END
      """.stripMargin
    checkTextHasError(text)

    val code =
      """
        |def foo(a: Any) = {}
        |foo(<caret>1, 2)
      """.stripMargin
    val result =
      """
        |def foo(a: Any) = {}
        |foo((1, 2))
      """.stripMargin
    testFix(code, result, hint)
  }

  def testSAMNotHighlighted(): Unit = {
    val text =
      """
        |trait SAM {
        |  def foo(): Int
        |}
        |def foo(s: SAM) = s.foo()
        |foo(() => 2)
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testAutoTupledSAMsAreHighlighted(): Unit = {
    val text =
      s"""
         |def foo(a: Any) = {}
         |foo$START(() => println("foo"),  () => 2)$END
      """.stripMargin
    checkTextHasError(text)

    val code =
      """
        |def foo(a: Any) = {}
        |foo(<caret>() => println("foo"),  () => 2)
      """.stripMargin
    val result =
      """
        |def foo(a: Any) = {}
        |foo((() => println("foo"), () => 2))
      """.stripMargin
    testFix(code, result, hint)
  }

  def testSAMNotHighlightedWhenTypesOfParametersOfAnonymousFunctionAreInferred(): Unit = {
    val text =
      """
        |trait SAM {
        |  def foo(s: String): Char
        |}
        |def bar(sam: SAM) = s.foo("foo")
        |
        |bar(j => j.charAt(0))
      """.stripMargin
    checkTextHasNoErrors(text)
  }

}
