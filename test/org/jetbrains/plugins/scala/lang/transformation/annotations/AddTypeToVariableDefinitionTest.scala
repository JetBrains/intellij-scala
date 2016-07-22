package org.jetbrains.plugins.scala.lang.transformation.annotations

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class AddTypeToVariableDefinitionTest extends TransformerTest(new AddTypeToVariableDefinition()) {
  def testSinglePattern() = check(
    "var v = new A()",
    "var v: A = new A()"
  )

  def testMultiplePatterns() = check(
    "var v1, v2 = new A()",
    "var v1, v2: A = new A()"
  )

  def testSimpleNameBinding() = check(
    "import scala.io.Source",
    "var v = new Source()",
    "var v: Source = new Source()"
  )

  def testExplicitType() = check(
    "var v: A = new A()",
    "var v: A = new A()"
  )

  def testDeclaration() = check(
    "var v: A",
    "var v: A"
  )

  def testValue() = check(
    "val v = new A()",
    "val v = new A()"
  )

  def testCaseClause() = check(
    "A match { case v => }",
    "A match { case v => }"
  )
}
