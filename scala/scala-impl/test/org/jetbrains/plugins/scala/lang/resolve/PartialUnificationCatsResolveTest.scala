package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.project._

class PartialUnificationCatsResolveTest extends ScalaLightCodeInsightFixtureTestAdapter with SimpleResolveTestBase {
  import SimpleResolveTestBase._

  override implicit val version: ScalaVersion = Scala_2_12

  override def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+ IvyManagedLoader("org.typelevel" %% "cats-core" % "1.4.0")

  override def setUp(): Unit = {
    super.setUp()
     getModule.scalaCompilerSettings.additionalCompilerOptions = Seq("-Ypartial-unification")
  }

  def testFunctionMap(): Unit = doResolveTest(
    s"""
       |import cats.instances.function._
       |import cats.syntax.functor._
       |
       |object fart extends App {
       |  val func1 = (a: Int) => a + 1
       |  val func2 = (a: Int) => a * 2
       |  val func3 = func1 m${REFSRC}ap func2
       |  println(func3(10))
       |}
    """.stripMargin, "FunctionMap.scala"
  )

  def testTupleMapN(): Unit = doResolveTest(
    s"""
       |case class Foo(s:String)
       |case class CountryCode(s: String)
       |object Foo {
       |  import cats.data.Validated
       |  import cats.syntax.validated._
       |
       |  private def hasIsoLengthCats(s: String): Validated[String,String] =
       |    if (s.length == 2) s.valid else "must have length 2".invalid
       |
       |  private def belongsToIsoOfficialCats(s: String): Validated[String,String] =
       |    if (s.startsWith("f")) s.valid else "must start with 'f'".invalid
       |
       |  def validated(s:String): Validated[String,CountryCode]={
       |    val x=(
       |      hasIsoLengthCats(s),
       |      belongsToIsoOfficialCats(s)
       |    )
       |
       |    import cats.syntax.apply._
       |    import cats.instances.string._
       |
       |    x.m${REFSRC}apN((s,_) => CountryCode(s))
       |  }
       |}
     """.stripMargin, "TupleMapN.scala"
  )

  def testIorFoldMap(): Unit = doResolveTest(
    s"""
       |import cats.implicits._
       |import cats.data.Ior
       |
       |val ior: Ior[Int, String] = ???
       |ior.fol${REFSRC}dMap(_.toString)
     """.stripMargin
  )

  def testSCL14782(): Unit = doResolveTest(
    s"""
       |import cats.data._
       |import cats.implicits._
       |object test {
       |  trait BaseError
       |  trait SubError extends BaseError
       |  def fn(o: Double, oli: List[Double]): ValidatedNel[BaseError, String] = {
       |    val va: ValidatedNel[BaseError, String] = ???
       |    val vb: ValidatedNel[SubError, String] = ???
       |    (va, vb).m${REFSRC}apN(
       |      (va: String, vb: String) => {
       |        ???
       |      }
       |    )
       |  }
       |}
     """.stripMargin
  )

  def testSCL15057(): Unit = doResolveTest(
    s"""
       |object example extends App {
       |  implicit class InvariantMap[F[_], A](fa: F[A]) {
       |    def map[B](f: A => B): F[B] = ???
       |  }
       |  implicit class CovariantMap[F[+_, _], E, A](fa: F[E, A]) {
       |    def m${REFTGT}ap[B](f: A => B): F[E, B] = ???
       |  }
       |  def covariantUser[F[+_, _]](fa: F[Nothing, Int]): Unit = {
       |    fa.m${REFSRC}ap(_.toString)
       |  }
       |}
     """.stripMargin
  )

  def testUnificationWrongKind(): Unit = checkTextHasNoErrors(
    """
      |trait IO[A]
      |trait Request[F[_]]
      |trait Response[F[_]]
      |trait Kleisli[F[_], A, B]
      |trait OptionT[F[_], A]
      |
      |type HttpService[F[_]] = Kleisli[({ type λ[β$0$] = OptionT[IO, β$0$] })#λ, Request[F], Response[F]]
      |
      |def f[F[_], A](fa: F[A]): F[A] = fa
      |val service: HttpService[IO] = ???
      |val fa: Kleisli[({ type λ[β$0$] = OptionT[IO, β$0$] })#λ, Request[IO], Response[IO]] = f(service)
    """.stripMargin
  )

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

}
