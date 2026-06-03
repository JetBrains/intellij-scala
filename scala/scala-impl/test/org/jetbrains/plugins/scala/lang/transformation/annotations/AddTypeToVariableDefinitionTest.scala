package org.jetbrains.plugins.scala
package lang
package transformation
package annotations

class AddTypeToVariableDefinitionTest extends TransformerTest(new AddTypeToVariableDefinition()) {

  def testSinglePattern(): Unit = check(
    before = "var v = new A()",
    after = "var v: A = new A()"
  )()

  def testMultiplePatterns(): Unit = check(
    before = "var v1, v2 = new A()",
    after = "var v1, v2: A = new A()"
  )()

  def testSimpleNameBinding(): Unit = check(
    before = "var v = new Source()",
    after = "var v: Source = new Source()"
  )(header = TransformationTest.ScalaSourceHeader)

  def testExplicitType(): Unit = check(
    before = "var v: A = new A()",
    after = "var v: A = new A()"
  )()

  def testDeclaration(): Unit = check(
    before = "var v: A",
    after = "var v: A"
  )()

  def testValue(): Unit = check(
    before = "val v = new A()",
    after = "val v = new A()"
  )()

  def testCaseClause(): Unit = check(
    before = "A match { case v => }",
    after = "A match { case v => }"
  )()
}
