package org.jetbrains.plugins.scala.lang.transformation
package declarations

/**
  * @author Pavel Fatin
  */
class ExpandProcedureSyntaxTest extends TransformerTest(new ExpandProcedureSyntax()) {
  def testProcedureSyntax() = check(
    "def f() {}",
    "def f(): Unit = {}"
  )

  def testNonUnitExpression() = check(
    "def f() { A }",
    "def f(): Unit = { A }"
  )

  def testElementPreservation() = check(
    "def f[A, B, C](a: A, b: B)(c: C) { A; B; C }",
    "def f[A, B, C](a: A, b: B)(c: C): Unit = { A; B; C }"
  )

  def testImplicitUnitType() = check(
    "def f() = {}",
    "def f() = {}"
  )

  def testExplicitUnityType() = check(
    "def f(): Unit = {}",
    "def f(): Unit = {}"
  )

  def testExplicitUnitTypeWithoutBraces() = check(
    "def f(): Unit = ()",
    "def f(): Unit = ()"
  )

  def testDefinitionImplicitType() = check(
    "def f()",
    "def f()"
  )

  def testDefinitionExplicitType() = check(
    "def f(): Unit",
    "def f(): Unit"
  )
}
