package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.psi.util.ScalaConstantExpressionEvaluator

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
    configureFromFileTextAdapter(getTestName(true) + ".scala", text)
    val evaluated = evaluate(getFileAdapter)
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
}