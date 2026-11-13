package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class CurriedTypeParamsApplyCallTest extends TypeInferenceTestBase {
  def testSCL22917_1(): Unit = doTest(
    s"""
       |object A {
       |  def m[A] = new C[A]
       |  class C[A] {
       |    def apply[B](f: A => (B)) = f
       |  }
       |
       |  ${START}m[Int](_.toString -> false)$END
       |}
       |//Int => (String, Boolean)
       |""".stripMargin
  )

  def testSCL22917_2(): Unit = doTest(
    s"""
       |object A {
       |  def m[A] = new C[A]
       |  class C[A] {
       |    def apply[B, C](f: A => (B, C)) = f
       |  }
       |  ${START}m[Int](_.toString -> false)$END
       |}
       |//Int => (String, Boolean)
       |""".stripMargin
  )

  def testSCL23327(): Unit = checkTextHasNoErrors(
    s"""
       |object Main {
       |    trait IO[A]
       |    trait GenSpawn[F[_], E]
       |    trait Async[F[_]] extends GenSpawn[F, Throwable]
       |    def apply[F[_], E](implicit F: GenSpawn[F, E]): F.type = F
       |    def apply[F[_]](implicit F: GenSpawn[F, _], d: DummyImplicit): F.type = F
       |    implicit val x: Async[IO] = ???
       |
       |    val y = apply[IO]
       |    val _: Async[IO] = y
       |}
       |""".stripMargin
  )
}

/**
 * The type parameters of an invoked method without parameters, which are inferred from the arguments of the `apply`
 * method of its result, e.g. `R` of `def scoped[R]: Scoped[R]` in `scoped { ... }`. The expected types of the
 * arguments must leave them undetermined, as the type parameters of `apply` itself, SCL-22614.
 *
 * The types are inferred by scalac 2.13.14 and by dotc 3.7.4.
 */
abstract class CurriedTypeParamsInferredFromApplyArgumentsTestBase extends TypeInferenceTestBase {

  // The body of the function literal is checked against `Option[A]`
  def testFunctionLiteralBody(): Unit = assertNoErrors(
    """object Test {
      |  def m[A] = new C[A]
      |
      |  class C[A] {
      |    def apply(f: Int => Option[A]): A = ???
      |  }
      |
      |  val a = m(x => Some(x))
      |  val b: Int = a
      |}
      |""".stripMargin
  )

  // The function literal of `flatMap` expects `Z[R1, E1, B]`, where `R1 >: Scope with R`
  def testSCL22614(): Unit = assertNoErrors(
    """trait Z[-R, +E, +A] {
      |  def flatMap[R1 <: R, E1 >: E, B](k: A => Z[R1, E1, B]): Z[R1, E1, B]
      |  def map[B](f: A => B): Z[R, E, B]
      |}
      |
      |trait Scope
      |
      |trait L[-RIn, +E, +ROut]
      |
      |object L {
      |  def scoped[R]: Scoped[R] = new Scoped[R]
      |
      |  final class Scoped[R] {
      |    def apply[E, A](z: => Z[Scope with R, E, A]): L[R, E, A] = ???
      |  }
      |}
      |
      |object Example {
      |  val z1: Z[String, Nothing, String] = ???
      |  val z2: Z[String, Nothing, String] = ???
      |
      |  def forComprehension = L.scoped {
      |    for {
      |      _ <- z1
      |      _ <- z2
      |    } yield ()
      |  }
      |
      |  def desugared = L.scoped(z1.flatMap(_ => z2.map(_ => ())))
      |
      |  val a: L[String, Nothing, Unit] = forComprehension
      |  val b: L[String, Nothing, Unit] = desugared
      |}
      |""".stripMargin
  )
}

final class CurriedTypeParamsInferredFromApplyArgumentsTest_Scala2 extends CurriedTypeParamsInferredFromApplyArgumentsTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13
}

final class CurriedTypeParamsInferredFromApplyArgumentsTest_Scala3 extends CurriedTypeParamsInferredFromApplyArgumentsTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3
}
