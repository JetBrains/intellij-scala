package org.jetbrains.plugins.scala
package lang
package transformation
package functions

class ExpandEtaExpansionTest extends TransformerTest(new ExpandEtaExpansion()) {

  def testExplicit(): Unit = check(
    before = "f _",
    after = "(a: A) => f(a)"
  )(header = "def f(a: A) = _")

  def testExplicitNoParameters(): Unit = check(
    before = "f _",
    after = "() => f"
  )(header = "def f = _")

  def testExplicitMultipleParameters(): Unit = check(
    before = "f _",
    after = "(a: A, b: B) => f(a, b)"
  )(header = "def f(a: A, b: B) = _")

  def testExplicitMultipleClauses(): Unit = check(
    before = "f _",
    after = "(a: A) => (b: B) => f(a)(b)"
  )(header = "def f(a: A)(b: B) = _")

  def testExplicitCurrying(): Unit = check(
    before = "f(a) _",
    after = "(b: B) => f(a)(b)"
  )(header = "def f(a: A)(b: B) = _")

  def testImplicit(): Unit = check(
    before = "val v: A => B = f",
    after = "val v: A => B = a => f(a)"
  )(header = "def f(a: A): B = _")

  def testImplicitNoParameters(): Unit = check(
    before = "val v: () => A = f", // not applicable
    after = "val v: () => A = f"
  )(header = "def f: A = _")

  def testImplicitMultipleParameters(): Unit = check(
    before = "val v: (A, B) => C = f",
    after = "val v: (A, B) => C = (a, b) => f(a, b)"
  )(header = "def f(a: A, b: B): C = _")

  def testImplicitMultipleClauses(): Unit = check(
    before = "val v: A => B => C = f",
    after = "val v: A => B => C = a => b => f(a)(b)"
  )("def f(a: A)(b: B): C = _")

  def testImplicitCurrying(): Unit = check(
    before = "val v: B => C = f(a)",
    after = "val v: B => C = b => f(a)(b)"
  )(header = "def f(a: A)(b: B): C = _")

  def testNaming(): Unit = check(
    before = "f _",
    after = "(foo: A) => f(foo)"
  )(header = "def f(foo: A) = _")

  def testArbitraryArgument(): Unit = check(
    before = "f(c) _",
    after = "(b: B) => f(c)(b)"
  )(header = "def f(a: A)(b: B) = _")

  // "easter egg" syntax (by-name parameter is actually a method)
  def testExplicitByNameParameter(): Unit = check(
    before = "def f(a: => A) = a _",
    after = "def f(a: => A) = () => a"
  )()

  def testImplicitByNameParameter(): Unit = check(
    before = "def f(a: => A): Unit = { val v: () => A = a }", // not applicable
    after = "def f(a: => A): Unit = { val v: () => A = a }"
  )()

  def testExplicitNormalParameter(): Unit = check(
    before = "def f(a: A) = a _",
    after = "def f(a: A) = a _"
  )()

  // TODO Java methods
}
