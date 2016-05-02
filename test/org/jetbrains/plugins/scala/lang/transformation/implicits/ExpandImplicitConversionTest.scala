package org.jetbrains.plugins.scala.lang.transformation.implicits

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

import scala.language.implicitConversions

/**
  * @author Pavel Fatin
  */
class ExpandImplicitConversionTest extends TransformerTest(ExpandImplicitConversion) {
  def testTypeConversionMethod() = check(
    "implicit def f(p: A): B = _",
    "val v: B = A",
    "val v: B = f(A)"
  )

  def testTypeConversionFunction() = check(
    "implicit val f: A => B = _",
    "val v: B = A",
    "val v: B = f(A)"
  )

  def testTypeConversionClass() = check(
    "implicit class Foo(val p: A) extends B",
    "val b: B = A",
    "val b: B = Foo(A)"
  )

  def testMethodCall() = check(
    "implicit class Foo(val p: A) extends B",
    "A.b()",
    "Foo(A).b()"
  )

  def testUnimported() = check(
    """
      class O
      object O {
        implicit def f(p: A): O = _
      }
    """,
    "val v: O = A",
    "val v: O = O.f(A)"
  )

// TODO
//  def testIndirection() = check(
//    """
//      class T {
//        implicit def f(p: A): O = _
//      }
//      class O
//      object O extends T
//    """,
//    "val v: O = A",
//    "val v: O = O.f(A)"
//  )
}
