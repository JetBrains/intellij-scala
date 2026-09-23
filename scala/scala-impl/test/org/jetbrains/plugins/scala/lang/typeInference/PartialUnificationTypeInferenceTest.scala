package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettingsProfile

class PartialUnificationTypeInferenceTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_2_13

  override protected def setUp(): Unit = {
    super.setUp()
    val profile = ScalaCompilerSettingsProfile.forModule(getModule)
    val newSettings = profile.getSettings.copy(
      additionalCompilerOptions = Seq("-Ypartial-unification")
    )
    profile.setSettings(newSettings)
  }

  def testSCL24497(): Unit = doTest {
      s"""
         |trait IO[+A]
         |object IO {
         |  def f[A](thunk: A): IO[A] = ???
         |}
         |
         |object example {
         |  def foo2[T[_], A](tioa: T[IO[A]]): IO[T[A]] = ???
         |  ${START}foo2(List.apply(IO.f(1)))$END
         |}
         |//IO[List[Int]]
         |""".stripMargin
  }

  def testSCL11320(): Unit = doTest(
    """
      |case class Foo[F[_], A](fab: F[Option[A]])
      |case class Bar[F[_], A](value: F[A])
      |/*start*/Foo(Bar(List.empty[Option[String]]))/*end*/
      |//Foo[[p0$$] Bar[List, p0$$], String]
    """.stripMargin
  )

  def testSCL11320_1(): Unit = doTest(
    """
      |case class Foo[F[_], A](fab: F[Option[A]])
      |case class Bar[F[_], A](value: F[A])
      |var y = Foo(Bar(List.empty[Option[String]]))
      |/*start*/y.fab/*end*/
      |//Bar[List, Option[String]]
    """.stripMargin
  )

  def testLub(): Unit = doTest(
    s"""
      |trait Foo
      |class Bar extends Foo
      |class Baz extends Foo
      |
      |def f[F[_], A](ffa: F[F[A]]): F[F[A]] = ffa
      |val a: Either[Bar, Either[Baz, Int]] = ???
      |${START}f(a)$END
      |//Either[Foo, Either[Foo, Int]]
    """.stripMargin
  )

  def testSCL25946_2(): Unit = checkTextHasNoErrors(
    """
      |class Test {
      |  class Root[E, R](val r: R)
      |  case class Leaf[R, E <: String](override val r: R) extends Root[E, R](r)
      |
      |  trait M[F[_ <: String]] {
      |    def r[R <: String](f: F[R]): R
      |  }
      |
      |  implicit def m[E]: M[({ type L[T <: String] = Leaf[Int, T] })#L] = ???
      |
      |  def M[F[_ <: String]: M, R <: String](f: F[R]): R = implicitly[M[F]].r(f)
      |
      |  M(Leaf[Int, String](1))
      |}
      """.stripMargin
  )

  def testSCL25946(): Unit = checkHasErrorAroundCaret(
    """
      |class Test {
      |  class Root[E, R](val r: R)
      |  case class Leaf[R, E <: String](override val r: R) extends Root[E, R](r)
      |
      |  trait M[F[_]] {
      |    def r[R](f: F[R]): R
      |  }
      |
      |  implicit def m[E]: M[({ type L[T] = Root[E, T] })#L] = new M[({ type L[T] = Root[E, T] })#L] {
      |    override def r[R](f: Root[E, R]): R = f.r
      |  }
      |
      |  def M[F[_]: M, R](f: F[R]): R = implicitly[M[F]].r(f)
      |
      |  M(Leaf[Int, String](1))$CARET
      |}
      """.stripMargin
  )
}


class PartialUnificationBoundsCheckingTypeInferenceTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3

  def testSCL25946(): Unit = checkTextHasNoErrors(
    """
      |class Test {
      |  class Root[E, R](val r: R)
      |  case class Leaf[R, E <: String](override val r: R) extends Root[E, R](r)
      |
      |  trait M[F[_]] {
      |    def r[R](f: F[R]): R
      |  }
      |
      |  implicit def m[E]: M[({ type L[T] = Root[E, T] })#L] = new M[({ type L[T] = Root[E, T] })#L] {
      |    override def r[R](f: Root[E, R]): R = f.r
      |  }
      |
      |  def M[F[_]: M, R](f: F[R]): R = implicitly[M[F]].r(f)
      |
      |  M(Leaf[Int, String](1))
      |}
      """.stripMargin
  )

  def testSCL25946_2(): Unit = checkHasErrorAroundCaret(
    s"""
      |class Test {
      |  class Root[E, R](val r: R)
      |  case class Leaf[R, E](override val r: R) extends Root[E, R](r)
      |
      |  trait M[F[_]] {
      |    def r[R](f: F[R]): R
      |  }
      |
      |  implicit def m[E]: M[({ type L[T] = Root[E, T] })#L] = new M[({ type L[T] = Root[E, T] })#L] {
      |    override def r[R](f: Root[E, R]): R = f.r
      |  }
      |
      |  def M[F[_]: M, R](f: F[R]): R = implicitly[M[F]].r(f)
      |
      |  M$CARET(Leaf[Int, String](1))
      |}
      |""".stripMargin
  )
}