package org.jetbrains.plugins.scala.lang.types.kindProjector

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration


abstract class KindProjectorHighlightingTestBase extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val settings       = defaultProfile.getSettings

    val newSettings = settings.copy(
      plugins = settings.plugins :+ "kind-projector"
    )

    defaultProfile.setSettings(newSettings)
  }
}

class KindProjectorHighlightingTestXSource3 extends KindProjectorHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_2_13

  override def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val settings       = defaultProfile.getSettings

    val newSettings = settings.copy(
      additionalCompilerOptions = settings.additionalCompilerOptions :+ "-P:kind-projector:underscore-placeholders" :+ "-Xsource:3"
    )

    defaultProfile.setSettings(newSettings)

  }

  def testUnderscorePlaceholders(): Unit =
    checkTextHasNoErrors(
      """
        |trait Foo[F[+_]]
        |def b: Foo[Either[Int, +_]] = ???
        |""".stripMargin
    )

  def testUnderscorePlaceholdersSimpleUnderscore(): Unit = {
    checkTextHasNoErrors(
      """
        |class TC[F[_]]
        |object TC {
        |  implicit val tc: TC[Either[Int, *]] = new TC
        |  implicit val tc1: TC[Either[Long, +*]] = new TC
        |}
        |object App extends App {
        |  println(implicitly[TC[Either[Int, _]]])
        |  println(implicitly[TC[Either[Long, +_]]])
        |}
        |""".stripMargin
    )
  }
}


class KindProjectorHighlightingTest extends KindProjectorHighlightingTestBase {
  override protected def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val settings       = defaultProfile.getSettings

    val newSettings = settings.copy(
      plugins = settings.plugins :+ "kind-projector",
    )

    defaultProfile.setSettings(newSettings)
  }

  def testSimple(): Unit =
    checkTextHasNoErrors(
      """
        |trait To[F[_], G[_]] {
        |  def apply[A](fa: F[A]): G[A]
        |}
        |
        |val a: List To Option = λ[List To Option](_.headOption)
      """.stripMargin
    )

  def testCustomMethod(): Unit =
    checkTextHasNoErrors(
      """
        |trait PF[-F[_], +G[_]] {
        |  def run[A](fa: F[A]): G[A]
        |}
        |
        |val f: PF[List, Option] = Lambda[PF[List, Option]].run(_.headOption)
      """.stripMargin
    )

  def testWithTypeLevelLambda(): Unit =
    checkTextHasNoErrors(
      """
        |trait Convert[-F[_], +G[_]] {
        |  def apply[A](fa: F[A]): G[A]
        |}
        |
        |val h: Convert[Either[Unit, ?], Option] = λ[Either[Unit, ?] Convert Option](_.fold(_ => None, a => Some(a)))
      """.stripMargin
    )

  def testFunctionKCats(): Unit =
    checkTextHasNoErrors(
      """
        |case class EitherK[F[_], G[_], A](run: Either[F[A], G[A]]) {
        |  def fold[H[_]](f: FunctionK[F, H], g: FunctionK[G, H]): H[A] =
        |    run.fold(f.apply, g.apply)
        |}
        |
        |case class Tuple2K[F[_], G[_], A](first: F[A], second: G[A])
        |
        |trait FunctionK[F[_], G[_]] extends Serializable { self =>
        |
        |  def apply[A](fa: F[A]): G[A]
        |
        |  def compose[E[_]](f: FunctionK[E, F]): FunctionK[E, G] =
        |    λ[FunctionK[E, G]](fa => self(f(fa)))
        |
        |  def andThen[H[_]](f: FunctionK[G, H]): FunctionK[F, H] =
        |    f.compose(self)
        |
        |  def or[H[_]](h: FunctionK[H, G]): FunctionK[EitherK[F, H, ?], G] =
        |    λ[FunctionK[EitherK[F, H, ?], G]](fa => fa.fold(self, h))
        |
        |  def and[H[_]](h: FunctionK[F, H]): FunctionK[F, Tuple2K[G, H, ?]] =
        |    λ[FunctionK[F, Tuple2K[G, H, ?]]](fa => Tuple2K(self(fa), h(fa)))
        |}
      """.stripMargin
    )

  def testSymbolic(): Unit =
    checkTextHasNoErrors(
      """
        |trait NaturalTransformation[-F[_], +G[_]] {
        |  def run[A](fa: F[A]): G[A]
        |}
        |
        |type ~>[F[_], G[_]] = NaturalTransformation[F, G]
        |
        |type Id[A] = A
        |val g: Id ~> Option = λ[Id ~> Option].run(x => Some(x))
      """.stripMargin
    )

  def testFreeInterpreter(): Unit =
    checkTextHasNoErrors(
      """
        |sealed abstract class Free[S[_], A] {
        |  final def mapK[T[_]](f: S ~> T): Free[T, A] = ???
        |}
        |
        |sealed abstract class Coyoneda[F[_], A] {
        |  final def mapK[T[_]](f: F ~> T): Coyoneda[T, A] = ???
        |}
        |type FreeC[S[_], A] = Free[Coyoneda[S, ?], A]
        |trait ~>[-F[_], +G[_]] {
        |  def apply[A](fa: F[A]): G[A]
        |}
        |
        |def injectFC[F[_], G[_]](implicit fk: F ~> G): FreeC[F, ?] ~> FreeC[G, ?]
        |  = λ[FreeC[F, ?] ~> FreeC[G, ?]](
        |    _.mapK(λ[Coyoneda[F, ?] ~> Coyoneda[G, ?]](_.mapK(fk)))
        |  )
        |
      """.stripMargin
    )

  def testSCL14759(): Unit =
    checkTextHasNoErrors(
      """
        |trait <:!<[A, B]
        |trait Monoid[A]
        |
        |def repeat[A: Monoid : Lambda[a => a <:!< Int]](a: A): Int = 42
      """.stripMargin
    )

  def testSCL18366(): Unit =
    checkTextHasNoErrors(
      """
        |sealed abstract class Resource[+F[_], +A] {
        |  def flatMap[G[x] >: F[x], B](f: A => Resource[G[*], B]): Resource[G[*], B] = ???
        |  def map[G[x] >: F[x], B](f: A => B): Resource[G[*], B] = ???
        |}
        |
        |trait S[F[_]]
        |object A {
        |  def xxx[U[_], Q[_]]: Resource[U, S[U]] = {
        |    val r1: Resource[U, Int] = ???
        |    val r2: Resource[U, Int] = ???
        |    val s: S[U] = ???
        |
        |    r1.flatMap(_ => r2.map(_ => s))
        |  }
        |}
        |""".stripMargin
    )


  def testLambdaType(): Unit =
    checkTextHasNoErrors(
      """
        |trait Hk[F[_]]
        |
        |def test: Hk[Lambda[x => List[x]]] = ???
        |""".stripMargin
    )
}
