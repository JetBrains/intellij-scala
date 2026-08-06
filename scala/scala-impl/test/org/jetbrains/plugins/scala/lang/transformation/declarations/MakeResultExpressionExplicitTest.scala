package org.jetbrains.plugins.scala
package lang
package transformation
package declarations

class MakeResultExpressionExplicitTest extends TransformerTest(new MakeResultExpressionExplicit()) {

  def testResultExpression(): Unit = check(
    before = "def f(): A = A",
    after = "def f(): A = return A"
  )()

  def testMultipleExpressions(): Unit = check(
    before = "def f(): A = if (true) A else A",
    after = "def f(): A = if (true) return A else return A"
  )()

  def testNoExplicitResultType(): Unit = check(
    before = "def f() = A",
    after = "def f() = A"
  )()

  def testUnitResultType(): Unit = check(
    before = "def f(): Unit = A",
    after = "def f(): Unit = A"
  )()

  def testIntermediateExpression(): Unit = check(
    before = "def f(): A = { A; B }",
    after = "def f(): A = { A; return B }"
  )()

  def testNoEnclosingMethod(): Unit = check(
    before = "{ A }",
    after = "{ A }"
  )()
}

