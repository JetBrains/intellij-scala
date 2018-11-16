package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}

class PartialUnificationTypeLambdaConformanceTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override implicit val version: ScalaVersion = Scala_2_12

  override def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+ IvyManagedLoader("org.typelevel" %% "cats-core" % "1.4.0")

  def testTypeLambdaConformance(): Unit = checkTextHasNoErrors(
    """
      |object Foo {
      |  trait T[F[_]]
      |  def fun[F[_], A](fa: F[A])(tf: T[F]): T[F] = tf
      |  val f: Int => String = ???
      |  val t: T[({ type L[A] = Int => A})#L] = ???
      |  fun(f)(t)
      |}
    """.stripMargin
  )

  def testEitherSequence(): Unit = checkTextHasNoErrors(
    """
      |import cats.implicits._
      |
      |val x: Either[String, Option[Either[String, Int]]] = Right(Some(Right(1)))
      |val y = x.flatMap(_.sequence)
      |val z = x.flatMap(_.traverse(identity))
    """.stripMargin
  )
}
