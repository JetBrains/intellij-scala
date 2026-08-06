package org.jetbrains.plugins.scala
package lang
package transformation
package declarations

class ExpandProcedureSyntaxTest extends TransformerTest(new ExpandProcedureSyntax()) {

  def testProcedureSyntax(): Unit = check(
    before = "def f(): Unit = {}",
    after = "def f(): Unit = {}"
  )()

  def testNonUnitExpression(): Unit = check(
    before = "def f(): Unit = { A }",
    after = "def f(): Unit = { A }"
  )()

  def testElementPreservation(): Unit = check(
    before = "def f[A, B, C](a: A, b: B)(c: C): Unit = { A; B; C }",
    after = "def f[A, B, C](a: A, b: B)(c: C): Unit = { A; B; C }"
  )()

  def testImplicitUnitType(): Unit = check(
    before = "def f(): Unit = {}",
    after = "def f(): Unit = {}"
  )()

  def testExplicitUnityType(): Unit = check(
    before = "def f(): Unit = {}",
    after = "def f(): Unit = {}"
  )()

  def testExplicitUnitTypeWithoutBraces(): Unit = check(
    before = "def f(): Unit = ()",
    after = "def f(): Unit = ()"
  )()

  def testDefinitionImplicitType(): Unit = check(
    before = "def f(): Unit",
    after = "def f(): Unit"
  )()

  def testDefinitionExplicitType(): Unit = check(
    before = "def f(): Unit",
    after = "def f(): Unit"
  )()
}
