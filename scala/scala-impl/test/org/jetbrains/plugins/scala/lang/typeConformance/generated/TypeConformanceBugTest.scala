package org.jetbrains.plugins.scala.lang.typeConformance
package generated

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class TypeConformanceBugTest extends TypeConformanceTestBase {
  override def folderPath: Path = super.folderPath / "bug"

  def testSCL2244(): Unit = {doTest()}

  def testSCL2549A(): Unit = {doTest()}

  def testSCL2549B(): Unit = {doTest()}

  def testSCL2978(): Unit = {doTest()}

  def testSCL3363(): Unit = {doTest()}

  def testSCL3364(): Unit = {doTest()}

  def testSCL3825(): Unit = {doTest()}

  def testSCL4278(): Unit = {doTest()}

  def testSCL9627(): Unit = {doTest()}

  def testSCL9877_2(): Unit = {doTest()}

  def testSCL9877_3(): Unit = {doTest()}

  def testSCL10237(): Unit = {doTest()}

  def testSCL10237_1(): Unit = {doTest()}

  def testSCL10237_2(): Unit = {doTest()}

  def testSCL10237_3(): Unit = {doTest()}

  def testSCL10237_Renamed(): Unit = {doTest()}

  def testSCL10432_1(): Unit = {doTest()}

  def testSCL10432_2(): Unit = {doTest()}

  def testSCL10357(): Unit = {doTest()}

  def testSCL8980_1(): Unit = {doTest()}

  def testSCL8980_2(): Unit = {doTest()}

  def testSCL11060(): Unit = {doTest()}

  def testSCL12202(): Unit = doTest()

  def testSCL11320(): Unit = {doTest(checkEquivalence = true)}

  def test3074(): Unit = doTest(
    """
      |val a: Array[Byte] = Array(1, 2, 3)
      |
      |/* True */
    """.stripMargin)

  def testSCL11140(): Unit = doTest(
    s"""
       |import scala.collection.{Map, mutable}
       |import scala.collection.generic.CanBuildFrom
       |
       |object IntelliBugs {
       |  implicit class MapOps[K2, V2, M[K, V] <: Map[K, V]](val m: M[K2, V2]) extends AnyVal {
       |    def mapValuesStrict[V3](f: V2 => V3)(implicit cbf: CanBuildFrom[M[K2, V2], (K2, V3), M[K2, V3]]) =
       |      m.map { case (k, v) => k -> f(v) }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    val m = mutable.HashMap.empty[String, Int]
       |
       |    $caretMarker
       |    val m2: Map[String, Long] = m.mapValuesStrict(_.toLong)
       |  }
       |}
       |//true
    """.stripMargin)

  def testSCL11060_2(): Unit = doTest(
    s"""
       |val foo: Iterator[(Int, Set[Int])] = {
       |  val tS: (Int, Set[Int]) = (5, Set(12,3))
       |  if (tS._2.nonEmpty) Some(tS).toIterator else None.toIterator
       |}
       |//true
    """.stripMargin)

  def testSCL15345(): Unit = doTest(
    s"""
       |trait Foo {
       |  type Self <: Foo { type Self <: Foo.this.Self }
       |  def chain: Self
       |}
       |val a: Foo = null
       |val a1: Foo = a.chain
       |val a2: Foo = a.chain.chain
       |//true
     """.stripMargin
  )


  def testSCL12540(): Unit = {
    doTest(
      """
        |trait Helper[A] {
        |  type Value = A
        |}
        |sealed trait Base
        |object Base extends Helper[Base] {
        |  case object Choice1 extends Value
        |  case object Choice2 extends Value
        |}
        |
        |val a: Base = Base.Choice1
        |//True
      """.stripMargin)
  }

  // An alias from an unrelated mixin is not the abstract member of the same name: scalac dealiases `K#M` to `Any`.
  def testMixedInAliasDoesNotConformToAbstractMemberOfSameName(): Unit = doTest(
    s"""
       |trait T0 { type M }
       |trait T1 extends T0
       |trait T2 { type M = Any }
       |trait K extends T1 with T2
       |val k: K = ???
       |${caretMarker}val x: T1#M = (??? : k.M)
       |//false
      """.stripMargin)

  // A class implementing an abstract type member of the same name does conform to it.
  def testClassImplementingAbstractTypeConformsToIt(): Unit = doTest(
    s"""
       |class A { type T <: S; class S }
       |class B extends A { class T extends S }
       |val b = new B
       |${caretMarker}val x: A#T = (??? : b.T)
       |//true
      """.stripMargin)

  // Null conforms to an abstract type only through its lower bound, as in scalac.
  def testNullDoesNotConformToAbstractTypeBoundedByClass(): Unit = doTest(
    s"""
       |class C
       |trait K { type D <: C }
       |val k: K = ???
       |${caretMarker}val x: k.D = null
       |//false
      """.stripMargin)

  def testNullConformsToAbstractTypeWithNullLowerBound(): Unit = doTest(
    s"""
       |class C
       |trait K { type F >: Null <: C }
       |val k: K = ???
       |${caretMarker}val x: k.F = null
       |//true
      """.stripMargin)

  def testNullDoesNotConformToAliasOfNothing(): Unit = doTest(
    s"""
       |trait K { type A = Nothing }
       |val k: K = ???
       |${caretMarker}val x: k.A = null
       |//false
      """.stripMargin)

  def testNullDoesNotConformToCompoundWithAbstractPart(): Unit = doTest(
    s"""
       |trait T { type M }
       |trait K
       |${caretMarker}val x: K with T#M = null
       |//false
      """.stripMargin)

  def testNullConformsToRefinedCompound(): Unit = doTest(
    s"""
       |trait K { type N }
       |${caretMarker}val x: K with String { type N = Int } = null
       |//true
      """.stripMargin)

  def testNullDoesNotConformToCompoundWithNothing(): Unit = doTest(
    s"""
       |${caretMarker}val x: Nothing with Any = null
       |//false
      """.stripMargin)
}
