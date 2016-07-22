package org.jetbrains.plugins.scala.lang.transformation.annotations

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class AddTypeToReferencePatternTest extends TransformerTest(new AddTypeToReferencePattern()) {
  def testCaseCaluse() = check(
    "(new A()) match { case v => }",
    "(new A()) match { case v: A => }"
  )

  def testGenerator() = check(
    "for (v <- new List[A]()) {}",
    "for (v: A <- new List[A]()) {}"
  )

  def testNestedPattern() = check(
    "case class P(v: A)",
    "val P(v) = P(new A())",
    "val P(v: A) = P(new A())"
  )

  def testSimpleNameBinding() = check(
    "import scala.io.Source",
    "(new Source()) match { case v => }",
    "(new Source()) match { case v: Source => }"
  )

  def testTypedPattern() = check(
    "(new A()) match { case v: A => }",
    "(new A()) match { case v: A => }"
  )

  def testValue() = check(
    "val v = new A()",
    "val v = new A()"
  )

  def testVariable() = check(
    "var v = new A()",
    "var v = new A()"
  )
}
