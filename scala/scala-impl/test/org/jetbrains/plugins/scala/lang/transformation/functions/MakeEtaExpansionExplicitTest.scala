package org.jetbrains.plugins.scala
package lang
package transformation
package functions

class MakeEtaExpansionExplicitTest extends TransformerTest(new MakeEtaExpansionExplicit()) {

  def testReference(): Unit = check(
    before = "val v: A => B = f",
    after = "val v: A => B = f _"
  )(header = "def f(a: A): B = _")

  def testCall(): Unit = check(
    before = "val v: B => C = f(a)",
    after = "val v: B => C = f(a) _"
  )(header = "def f(a: A)(b: B): C = _")

  def testReferenceNoExpectedType(): Unit = check(
    before = "f",
    after = "f"
  )(header = "def f(a: A): B = _")

  def testCallNoExpectedType(): Unit = check(
    before = "f(a)",
    after = "f(a)"
  )(header = "def f(a: A)(b: B): C = _")

  def testOrdinaryReference(): Unit = check(
    before = "f",
    after = "f"
  )(header = "def f: A = _")

  def testOrdinaryCall(): Unit = check(
    before = "f(A)",
    after = "f(A)"
  )(header = "def f(a: A): B = _")

  def testReferenceExplicitExpansion(): Unit = check(
    before = "f _",
    after = "f _"
  )(header = "def f(a: A): B = _")

  def testCallExplicitExpansion(): Unit = check(
    before = "f(a) _",
    after = "f(a) _"
  )(header = "def f(a: A)(b: B): C = _")

  // TODO test Java method
}
