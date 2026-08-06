package org.jetbrains.plugins.scala
package lang
package transformation
package annotations

class AddTypeToValueDefinitionTest extends TransformerTest(new AddTypeToValueDefinition()) {

  def testSinglePattern(): Unit = check(
    before = "val v = new A()",
    after = "val v: A = new A()"
  )()

  def testMultiplePatterns(): Unit = check(
    before = "val v1, v2 = new A()",
    after = "val v1, v2: A = new A()"
  )()

  def testSimpleNameBinding(): Unit = check(
    "val v = new Source()",
    "val v: Source = new Source()"
  )(header = TransformationTest.ScalaSourceHeader)

  def testExplicitType(): Unit = check(
    before = "val v: A = new A()",
    after = "val v: A = new A()"
  )()

  def testDeclaration(): Unit = check(
    before = "val v: A",
    after = "val v: A"
  )()

  def testVariable(): Unit = check(
    before = "var v = new A()",
    after = "var v = new A()"
  )()

  def testCaseClause(): Unit = check(
    before = "A match { case v => }",
    after = "A match { case v => }"
  )()
}
