package org.jetbrains.plugins.scala
package annotator
package template


class NeedsToBeAbstractBugsTest extends AnnotatorTestBase(NeedsToBeAbstract) {
  def testSCL2981() {
    assertMatches(messages("trait A { type T; def t(p: T)}; class B extends A { type T = Int; def t(p: T) = ()}")) {
      case Nil =>
    }
  }

  def testSCL3515() {
    assertMatches(messages("trait A { type T}; class B extends A")) {
      case Nil =>
    }
  }

  def testSCL3514() {
    val code = """
trait M[X]
abstract class A {
  def foo[A: M]
  def bar[A](implicit oa: M[A])
}

class B extends A {
  def foo[A](implicit oa: M[A]) = ()
  def bar[A: M] = ()
}
    """
    assertMatches(messages(code)) {
      case Nil =>
    }
  }
}