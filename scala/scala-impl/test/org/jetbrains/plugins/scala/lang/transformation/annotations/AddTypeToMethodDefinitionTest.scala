package org.jetbrains.plugins.scala
package lang
package transformation
package annotations

class AddTypeToMethodDefinitionTest extends TransformerTest(new AddTypeToMethodDefinition()) {

  def testImplicitType(): Unit = check(
    before = "def f() = new A()",
    after = "def f(): A = new A()"
  )()

  def testSimpleNameBinding(): Unit = check(
    before = "def f() = new Source()",
    after = "def f(): Source = new Source()"
  )(header = TransformationTest.ScalaSourceHeader)

  def testExplicitType(): Unit = check(
    before = "def f(): A = new A()",
    after = "def f(): A = new A()"
  )()

  def testProcedure(): Unit = check(
    before = "def f(): Unit = {}",
    after = "def f(): Unit = {}"
  )()

  def testDeclaration(): Unit = check(
    before = "def f(): Unit",
    after = "def f(): Unit"
  )()
}
