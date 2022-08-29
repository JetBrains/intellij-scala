package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.psi.util.ScalaConstantExpressionEvaluator

import scala.annotation.nowarn

@nowarn("msg=ScalaLightPlatformCodeInsightTestCaseAdapter")
class ScalaConstantExpressionEvaluatorTest extends ScalaLightPlatformCodeInsightTestCaseAdapter{

  val pattern = "/*fold after this comment*/"

  private def evaluate(file: PsiFile): AnyRef = {
    val text = file.getText
    val index = text.lastIndexOf(pattern)
    val element = file.findElementAt(index)
    val evaluator = new ScalaConstantExpressionEvaluator()
    evaluator.computeConstantExpression(element.getNextSibling, false)
  }

  private def check(text: String, expected: Any): Unit = {
    configureFromFileText(getTestName(true) + ".scala", text)
    val evaluated = evaluate(getFile)
    assert(evaluated == expected)
  }

  def testSimpleLiteral(): Unit = {
    check(
      s"""
        |object O {
        |  val foo = $pattern"foo"
        |}
      """.stripMargin, "foo")
  }

  def testLiteralInParens(): Unit = {
    check(
      s"""
         |object O {
         |  val foo = $pattern("foo")
         |}
       """.stripMargin, "foo"
    )
  }

  def testLiteralConcatenation(): Unit = {
    check(
      s"""
         |object O {
         |  val foo = $pattern"foo" + "bar"
         |}
       """.stripMargin, "foobar")
  }

  def testLiteralInterpolated(): Unit = {
    val prefix = "$prefix"
    check(
      s"""
         |object O {
         |  val prefix = "foo"
         |  val foo = ${pattern}s"$prefix-bar"
         |}
       """.stripMargin, "foo-bar"
    )
  }

  def testLiteralInterpolated2(): Unit = {
    val prefix = "${prefix}"
    check(
      s"""
         |object O {
         |  val prefix = "foo"
         |  val foo = ${pattern}s"${prefix}bar"
         |}
       """.stripMargin, "foobar"
    )
  }

  def testLiteralInterolated3(): Unit = {
    val prefix = "$prefix"
    val suffix = "$suffix"
    check(
      s"""
         |object O {
         |  val prefix = "foo"
         |  val suffix = "baz"
         |  val foo = ${pattern}s"${prefix}-bar$suffix"
         |}
       """.stripMargin, "foo-barbaz"
    )
  }

  def testInfixExpr(): Unit = {
    check(
      s"""
         |$pattern(1 + 2 + 3 + 4)
       """.stripMargin, 10)
  }

  def testUnOp(): Unit = {
    check(
      s"""
         |$pattern-3
       """.stripMargin, -3)
  }

  def testUnOp2(): Unit = {
    check(
      s"""
         |$pattern+(7.0 - 3)
       """.stripMargin, 4.0)
  }

  def testValInClass(): Unit = {
    check(
      s"""
         |class C {
         |  val a = 42
         |  val b = $pattern(a + 3)
         |}
       """.stripMargin, null)
  }

  def testVarInClass(): Unit = {
    check(
      s"""
         |class C {
         |  var a = 42
         |  val b = $pattern(a - 4)
         |}
       """.stripMargin, null)
  }

  def testDefInClass(): Unit = {
    check(
      s"""
         |class C {
         |  def foo = 42
         |  val b = $pattern(foo + foo)
         |}
       """.stripMargin, null)
  }

  def testVarInObject(): Unit = {
    check(
      s"""
         |object O {
         |  var a = 42
         |  val b = $pattern(a + 4)
         |}
       """.stripMargin, null)
  }

  def testDefInObject(): Unit = {
    check(
      s"""
         |object O {
         |  def foo = 42
         |  val b = $pattern(foo + foo)
         |}
       """.stripMargin, 84)
  }

  def testFinalValInClass(): Unit = {
    check(
      s"""
         |class C {
         |  final val foo = 42
         |  val b = $pattern(foo + 3)
         |}
       """.stripMargin, 45
    )
  }

  def testValInInnerScope(): Unit = {
    check(
      s"""
         |class C {
         |  {
         |    val foo = 4
         |    val b = $pattern(foo + foo)
         |  }
         |}
       """.stripMargin, 8)
  }

  def testFinalDefInObject(): Unit = {
    check(
      s"""
         |class C {
         |  final def foo = 4
         |  val b = $pattern(foo - 1)
         |}
       """.stripMargin, 3)
  }

  def testValInFinalClass(): Unit = {
    check(
      s"""
         |final class C {
         |  val foo = 4
         |  def bar = 5
         |  val b = $pattern(foo + bar)
         |}
       """.stripMargin, 9)
  }
}