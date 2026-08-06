package org.jetbrains.plugins.scala
package lang
package transformation
package implicits

import scala.language.implicitConversions

class ExpandImplicitConversionTest extends TransformerTest(new ExpandImplicitConversion()) {

  def testTypeConversionMethod(): Unit = check(
    before = "val v: B = A",
    after = "val v: B = f(A)"
  )(header = "implicit def f(p: A): B = _")

  def testTypeConversionFunction(): Unit = check(
    before = "val v: B = A",
    after = "val v: B = f(A)"
  )(header = "implicit val f: A => B = _")

  def testTypeConversionClass(): Unit = check(
    before = "val b: B = A",
    after = "val b: B = Foo(A)"
  )(header = "implicit class Foo(val p: A) extends B")

  def testMethodCall(): Unit = check(
    before = "A.b()",
    after = "Foo(A).b()"
  )(header = "implicit class Foo(val p: A) extends B")

  def testNotImported(): Unit = check(
    before = "val v: O = A",
    after = "val v: O = O.f(A)"
  )(header =
    """
      class O
      object O {
        implicit def f(p: A): O = _
      }
    """)

  // TODO
  //  def testIndirection(): Unit = check(
  //    before = "val v: O = A",
  //    after = "val v: O = O.f(A)"
  //  )(header =
  //    """
  //        class T {
  //          implicit def f(p: A): O = _
  //        }
  //        class O
  //        object O extends T
  //      """)
}
