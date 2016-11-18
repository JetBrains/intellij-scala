package org.jetbrains.plugins.scala.lang.transformation
package functions

/**
  * @author Pavel Fatin
  */
class ExpandEtaExpansionTest extends TransformerTest(new ExpandEtaExpansion()) {
  def testExplicit() = check(
    "def f(a: A) = _",
    "f _",
    "(a: A) => f(a)"
  )

  def testExplicitNoParameters() = check(
    "def f = _",
    "f _",
    "() => f"
  )

  def testExplicitMultipleParameters() = check(
    "def f(a: A, b: B) = _",
    "f _",
    "(a: A, b: B) => f(a, b)"
  )

  def testExplicitMultipleClauses() = check(
    "def f(a: A)(b: B) = _",
    "f _",
    "(a: A) => (b: B) => f(a)(b)"
  )

  def testExplicitCurrying() = check(
    "def f(a: A)(b: B) = _",
    "f(a) _",
    "(b: B) => f(a)(b)"
  )

  def testImplicit() = check(
    "def f(a: A): B = _",
    "val v: A => B = f",
    "val v: A => B = a => f(a)"
  )

  def testImplicitNoParameters() = check(
    "def f: A = _",
    "val v: () => A = f", // not applicable
    "val v: () => A = f"
  )

  def testImplicitMultipleParameters() = check(
    "def f(a: A, b: B): C = _",
    "val v: (A, B) => C = f",
    "val v: (A, B) => C = (a, b) => f(a, b)"
  )

  def testImplicitMultipleClauses() = check(
    "def f(a: A)(b: B): C = _",
    "val v: A => B => C = f",
    "val v: A => B => C = a => b => f(a)(b)"
  )

  def testImplicitCurrying() = check(
    "def f(a: A)(b: B): C = _",
    "val v: B => C = f(a)",
    "val v: B => C = b => f(a)(b)"
  )

  def testNaming() = check(
    "def f(foo: A) = _",
    "f _",
    "(foo: A) => f(foo)"
  )

  def testArbitraryArgument() = check(
    "def f(a: A)(b: B) = _",
    "f(c) _",
    "(b: B) => f(c)(b)"
  )

  // "easter egg" syntax (by-name parameter is actually a method)
  def testExplicitByNameParameter() = check(
    "def f(a: => A) = a _",
    "def f(a: => A) = () => a"
  )

  def testImplicitByNameParameter() = check(
    "def f(a: => A) = { val v: () => A = a }", // not applicable
    "def f(a: => A) = { val v: () => A = a }"
  )

  def testExplicitNormalParameter() = check(
    "def f(a: A) = a _",
    "def f(a: A) = a _"
  )

  // TODO Java methods
}
