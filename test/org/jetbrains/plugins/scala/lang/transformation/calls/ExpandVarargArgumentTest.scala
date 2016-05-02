package org.jetbrains.plugins.scala.lang.transformation.calls

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class ExpandVarargArgumentTest extends TransformerTest(ExpandVarargArgument,
  """
     object O {
       def f(v: A*) {}
       def g(v1: A, v2: B*) {}
     }
  """) {

  def testEmpty() = check(
    "O.f()",
    "O.f(Array(): _*)"
  )

  def testMultiple() = check(
    "O.f(A, A)",
    "O.f(Array(A, A): _*)"
  )

  def testTail() = check(
    "O.g(A, B, B)",
    "O.g(A, Array(B, B): _*)"
  )

// TODO
//  def testInfixSingle() = check(
//    "O f A",
//    "O f (Array(A, A): _*)"
//  )

// TODO
//  def testInfixMultiple() = check(
//    "O f (A, A)",
//    "O f (Array(A, A): _*)"
//  )

  def testSynthetic() = check(
    "case class T(v: A*)",
    "T.apply(A, A)",
    "T.apply(Array(A, A): _*)"
  )

// TODO
//  def testConstructor() = check(
//    "class T(v: A*)",
//    "new T(A, A)",
//    "new T(Array(A, A): _*)"
//  )

  def testExplicit() = check(
    "O.f(Array(A, A): _*)",
    "O.f(Array(A, A): _*)"
  )

  // the transformation is is infinitely recusive, as there's no Object[] {} equivalent in Scala
  def testArray() = check(
    "Array(A, A)",
    "Array(A, A)"
  )

  // TODO rely on _* instead of Array to prevent recursion
  // TODO support Java methods
}