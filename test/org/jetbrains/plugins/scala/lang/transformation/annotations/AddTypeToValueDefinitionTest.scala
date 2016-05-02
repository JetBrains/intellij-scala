package org.jetbrains.plugins.scala.lang.transformation.annotations

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class AddTypeToValueDefinitionTest extends TransformerTest(AddTypeToValueDefinition) {
  def testSinglePattern() = check(
    "val v = new A()",
    "val v: A = new A()"
  )

  def testMultiplePatterns() = check(
    "val v1, v2 = new A()",
    "val v1, v2: A = new A()"
  )

  def testExplicitType() = check(
    "val v: A = new A()",
    "val v: A = new A()"
  )

  def testDeclaration() = check(
    "val v: A",
    "val v: A"
  )

  def testVariable() = check(
    "var v = new A()",
    "var v = new A()"
  )

  def testCaseClause() = check(
    "A match { case v => }",
    "A match { case v => }"
  )
}
