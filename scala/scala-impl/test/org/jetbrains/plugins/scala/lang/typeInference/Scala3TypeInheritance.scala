package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class Scala3TypeInheritance extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_0

  def testScl24261(): Unit = {
    checkTextHasNoErrors(
      """
        |class Foo {
        |  def method: Foo = ???
        |}
        |
        |class Bar extends Foo {
        |  override def method = ??? : Bar // : Foo in Scala 3
        |
        |  var x = method
        |  x = ??? : Foo // Compiles in Scala 3
        |}
      """.stripMargin
    )
  }

  def testInterleavedClausesInOverrideReturnTypeInference(): Unit = {
    checkTextHasNoErrors(
      """
        |trait Base:
        |  def method[A](a: A)[B](b: B): (A, B) = (a, b)
        |
        |class Child extends Base:
        |  override def method[A](a: A)[B](b: B) = ???
        |
        |  def use[A](a: A)[B](b: B): A =
        |    val tuple = method(a)(b)
        |    tuple._1
      """.stripMargin
    )
  }
}

/** Before Scala 3.6, the evidence of context bounds is `implicit` */
@Category(Array(classOf[TypecheckerTests]))
class Scala3TypeInheritance_3_3 extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_3

  // The return type is inferred from `super.method[G, A](a)(f)`, which gets the evidence of the overridden method implicitly
  def testContextBoundsInOverrideReturnTypeInference(): Unit = {
    checkTextHasNoErrors(
      """
        |trait Applicative[F[_]]
        |given Applicative[Option] = new Applicative[Option] {}
        |
        |trait Base:
        |  def method[G[_]: Applicative, A](a: A)(f: A => G[A]): G[A]
        |
        |class Child extends Base:
        |  override def method[G[_]: Applicative, A](a: A)(f: A => G[A]) = f(a)
        |
        |  val option: Option[Int] = method[Option, Int](1)(Option(_))
      """.stripMargin
    )
  }
}
