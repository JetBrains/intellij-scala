package org.jetbrains.plugins.scala
package lang
package transformation
package annotations

class AddTypeToReferencePatternTest extends TransformerTest(new AddTypeToReferencePattern()) {

  def testCaseClause(): Unit = check(
    before = "(new A()) match { case v => }",
    after = "(new A()) match { case v: A => }"
  )()

  def testGenerator(): Unit = check(
    before = "for (v <- new List[A]()) {}",
    after = "for (v: A <- new List[A]()) {}"
  )()

  def testNestedPattern(): Unit = check(
    before = "val P(v) = P(new A())",
    after = "val P(v: A) = P(new A())"
  )(header = "case class P(v: A)")

  def testSimpleNameBinding(): Unit = check(
    before = "(new Source()) match { case v => }",
    after = "(new Source()) match { case v: Source => }"
  )(header = TransformationTest.ScalaSourceHeader)

  def testTypedPattern(): Unit = check(
    before = "(new A()) match { case v: A => }",
    after = "(new A()) match { case v: A => }"
  )()

  def testValue(): Unit = check(
    before = "val v = new A()",
    after = "val v = new A()"
  )()

  def testVariable(): Unit = check(
    before = "var v = new A()",
    after = "var v = new A()"
  )()
}
