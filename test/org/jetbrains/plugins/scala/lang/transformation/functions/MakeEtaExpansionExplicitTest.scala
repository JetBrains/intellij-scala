package org.jetbrains.plugins.scala.lang.transformation
package functions

/**
  * @author Pavel Fatin
  */
class MakeEtaExpansionExplicitTest extends TransformerTest(new MakeEtaExpansionExplicit()) {
  def testReference() = check(
    "def f(a: A): B = _",
    "val v: A => B = f",
    "val v: A => B = f _"
  )

  def testCall() = check(
    "def f(a: A)(b: B): C = _",
    "val v: B => C = f(a)",
    "val v: B => C = f(a) _"
  )

  def testReferenceNoExpectedType() = check(
    "def f(a: A): B = _",
    "f",
    "f"
  )

  def testCallNoExpectedType() = check(
    "def f(a: A)(b: B): C = _",
    "f(a)",
    "f(a)"
  )

  def testOrdinaryReference() = check(
    "def f: A = _",
    "f",
    "f"
  )

  def testOrdinaryCall() = check(
    "def f(a: A): B = _",
    "f(A)",
    "f(A)"
  )

  def testReferenceExplicitExpansion() = check(
    "def f(a: A): B = _",
    "f _",
    "f _"
  )

  def testCallExplictExpansion() = check(
    "def f(a: A)(b: B): C = _",
    "f(a) _",
    "f(a) _"
  )

  // TODO test Java method
}
