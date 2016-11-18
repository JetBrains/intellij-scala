package org.jetbrains.plugins.scala.lang.transformation
package declarations

/**
  * @author Pavel Fatin
  */
class MakeResultExpressionExplicitTest extends TransformerTest(new MakeResultExpressionExplicit()) {
  def testResultExpression() = check(
    "def f(): A = A",
    "def f(): A = return A"
  )

  def testMultipleExpressions() = check(
    "def f(): A = if (true) A else A",
    "def f(): A = if (true) return A else return A"
  )

  def testNoExplicitResultType() = check(
    "def f() = A",
    "def f() = A"
  )

  def testUnitResultType() = check(
    "def f(): Unit = A",
    "def f(): Unit = A"
  )

  def testIntermediateExpression() = check(
    "def f(): A = { A; B }",
    "def f(): A = { A; return B }"
  )

  def testNoEnclosingMethod() = check(
    "{ A }",
    "{ A }"
  )
}

