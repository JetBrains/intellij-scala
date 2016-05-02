package org.jetbrains.plugins.scala.lang.transformation.annotations

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class AddTypeToMethodDefinitionTest extends TransformerTest(AddTypeToMethodDefinition) {
  def testImplicitType() = check(
    "def f() = new A()",
    "def f(): A = new A()"
  )

  def testExplicitType() = check(
    "def f(): A = new A()",
    "def f(): A = new A()"
  )

  def testProcedure() = check(
    "def f() {}",
    "def f() {}"
  )

  def testDeclaration() = check(
    "def f()",
    "def f()"
  )
}
