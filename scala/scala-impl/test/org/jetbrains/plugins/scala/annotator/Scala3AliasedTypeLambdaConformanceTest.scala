package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class Scala3AliasedTypeLambdaConformanceTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  private val context: String =
    """
      |trait Bar[A]
      |val xs: Bar[String] = ???
      |def foo[F[_], A](fa: F[A]): F[A] = fa
      |""".stripMargin

  private def doTest(code: String): Unit =
    checkTextHasNoErrors(
      s"""
         |object Test {
         |$context
         |$code
         |}
         |""".stripMargin)

  def testSimpleLambda(): Unit = doTest(
    """
      |type TL = [A] =>> Bar[A]
      |foo[TL, String](xs)
      |""".stripMargin
  )

  def testMultiListLambda(): Unit = doTest(
    """
      |type TL2 = [A] =>> [B] =>> Bar[A]
      |foo[TL2[String], Int](xs)
      |""".stripMargin
  )

  def testAliasAndLambdaBothHaveTypeParameters(): Unit = doTest(
    """
      |type Foo[A] = [B] =>> [C] =>> Bar[B]
      |foo[Foo[Int][String], Double](xs)
      |""".stripMargin
  )

  def testChainedAlias(): Unit = doTest(
    """
      |type TL3 = [A] =>> [B] =>> Int
      |type C = TL3
      |foo[C[Int], String](1)
      |""".stripMargin
  )

  def testTypeLambdaNeg(): Unit = checkHasErrorAroundCaret(
    s"""
       |object Test {
       |  $context
       |  type TL4 = [A] =>> [B] =>> Bar[B]
       |  foo[TL4[String], Int](x${CARET}s)
       |}
       |""".stripMargin
  )

  def testSCL24299(): Unit = checkTextHasNoErrors(
    """
      |//NOTE: C1 & C2 are treated the same in the compiler type system
      |// Also tasty represents them in the exact same way
      |type C1[T] = MyContainer[T] // ~ type C1[T >: Nothing <: Any] = MyContainer[T]
      |
      |type C2 = [T] =>> MyContainer[T] // ~ type C1[T >: Nothing <: Any] = MyContainer[T]
      |
      |//From https://docs.scala-lang.org/scala3/reference/new-types/type-lambdas-spec.html:
      |//   A partially applied type constructor such as List is assumed to be equivalent to its eta expansion.
      |//   I.e, List = [X] =>> List[X]. This allows type constructors to be compared with type lambdas.
      |//Note (from Dmitrii), it seems they are represented differently in the compiler type trees and in Tasty
      |type C3 = MyContainer
      |
      |trait MyContainer[X] {
      |  def apply(): X = ??? //
      |  def fooTyped: X = ???
      |  def foo: String = ???
      |}
      |
      |@main def mainOk(): Unit = {
      |  // OK - conforms
      |  (??? : MyContainer[String]): C1[String]
      |  (??? : MyContainer[String]): C2[String]
      |  (??? : MyContainer[String]): C3[String]
      |
      |  // OK - conforms
      |  (??? : C1[String]): MyContainer[String]
      |  (??? : C2[String]): MyContainer[String]
      |  (??? : C3[String]): MyContainer[String]
      |
      |  (??? : C1[String]): C2[String]
      |  (??? : C1[String]): C3[String]
      |  (??? : C2[String]): C1[String]
      |  (??? : C2[String]): C3[String]
      |  (??? : C3[String]): C1[String]
      |  (??? : C3[String]): C2[String]
      |
      |  val x1: C1[String] = ???
      |  val x2: C2[String] = ???
      |  val x3: C3[String] = ???
      |
      |  // OK - resolves
      |  x1.apply().length
      |  x1().length
      |
      |  // ERROR: methods apply or fooTyped are not resolved
      |  x2().length
      |  x2.apply().length
      |  x2.fooTyped.length
      |  x2.foo.length
      |
      |
      |  // ERROR: methods apply or fooTyped are resolved,
      |  // but type argument `String` is not mapped to X
      |  x3().length
      |  x3.apply().length
      |  x3.fooTyped.length
      |  //OK
      |  x3.foo.length
      |}
      |""".stripMargin
  )

  def testSCL22692(): Unit = checkTextHasNoErrors(
    """
      |// f as "Polymorphic function" literal
      |// (type is inferred correctly at this moment as [X] => Seq[X] => Option[Int]
      |val f1 = [X] => (xs: Seq[X]) => Option(xs.length)
      |// ERROR: r is inferred as Any
      |val r1: Option[Int] = f1(Seq(1, 2, 3))
      |""".stripMargin
  )
}
