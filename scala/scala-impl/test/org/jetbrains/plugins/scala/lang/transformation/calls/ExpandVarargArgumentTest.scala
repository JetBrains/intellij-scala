package org.jetbrains.plugins.scala
package lang
package transformation
package calls

class ExpandVarargArgumentTest extends TransformerTest(new ExpandVarargArgument()) {

  override protected val header: String =
    """
     object O {
       def f(v: A*) {}
       def g(v1: A, v2: B*) {}
     }
  """

  def testEmpty(): Unit = check(
    before = "O.f()",
    after = "O.f(Array(): _*)"
  )()

  def testMultiple(): Unit = check(
    before = "O.f(A, A)",
    after = "O.f(Array(A, A): _*)"
  )()

  def testTail(): Unit = check(
    before = "O.g(A, B, B)",
    after = "O.g(A, Array(B, B): _*)"
  )()

  // TODO
  //  def testInfixSingle(): Unit = check(
  //    before = "O f A",
  //    after = "O f (Array(A, A): _*)"
  //  )()

  // TODO
  //  def testInfixMultiple(): Unit = check(
  //    before = "O f (A, A)",
  //    after = "O f (Array(A, A): _*)"
  //  )()

  def testSynthetic(): Unit = check(
    before = "T.apply(A, A)",
    after = "T.apply(Array(A, A): _*)"
  )(header = "case class T(v: A*)")

  // TODO
  //  def testConstructor(): Unit = check(
  //    before = "new T(A, A)",
  //    after = "new T(Array(A, A): _*)"
  //  )(header = "class T(v: A*)")

  def testExplicit(): Unit = check(
    before = "O.f(Array(A, A): _*)",
    after = "O.f(Array(A, A): _*)"
  )()

  // the transformation is is infinitely recusive, as there's no Object[] {} equivalent in Scala
  def testArray(): Unit = check(
    before = "Array(A, A)",
    after = "Array(A, A)"
  )()

  // TODO rely on _* instead of Array to prevent recursion
  // TODO support Java methods
}