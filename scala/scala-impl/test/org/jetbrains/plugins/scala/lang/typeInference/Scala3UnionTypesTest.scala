package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestLike
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class Scala3UnionTypesTest extends ScalaLightCodeInsightFixtureTestCase with ScalaHighlightingTestLike {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3

  def testAbstractTypeConformsToUnionContainingIt(): Unit = assertNoErrors(
    """object Test {
      |  def f1[T, B](t: T, b: B): T | B = b
      |  def f2[T, B](t: T, b: B): T | B = t
      |  def f3[T, A, B](t: T, b: B): T | B = t match {
      |    case _: A => b
      |    case _: T => t
      |  }
      |}
      |
      |trait Members {
      |  type A[_]
      |  type B[_]
      |  def b: B[Int]
      |  def aOrB: A[Int] | B[Int] = b
      |  def bOrA: B[Int] | A[Int] = b
      |}
      |""".stripMargin
  )

  def testAbstractTypeConformsToUnionWithItsUpperBound(): Unit = assertNoErrors(
    """object Test {
      |  def f[T <: Int](t: T): String | Int = t
      |}
      |""".stripMargin
  )

  // the alias is expanded before the components of the union are tried, which would constrain `U` too much
  def testAliasOfUnionWithTypeParameter(): Unit = assertNoErrors(
    """class Err
      |type Lifted[A] = Err | A
      |
      |def point[O](o: O): Lifted[O] = o
      |extension [O, U](o: Lifted[O]) def map(f: O => U): Lifted[U] = ???
      |
      |val error: Err = Err()
      |def ok: Int | Err = point("a").map(_ => if true then 1 else error)
      |def ok2: Int | Err = point("a").map(_ => 1)
      |""".stripMargin
  )

  def testAbstractTypeDoesNotConformToUnrelatedUnion(): Unit = assertErrorsText(
    """object Test {
      |  def f[T, B](b: B): T | Int = b
      |}
      |""".stripMargin,
    "Error(b,Expression of type B doesn't conform to expected type T | Int)"
  )
}
