package org.jetbrains.plugins.scala.lang.typeInference
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3DerivingTest extends ImplicitParametersTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testSimple(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait Eq[A]
       |object Eq { def derived[A]: Eq[A] = ??? }
       |
       |case class Foo(x: Int) derives Eq
       |object A {
       |  ${START}implicitly[Eq[Foo]]$END
       |}
       |""".stripMargin
  )

  def testEnum(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait Eq[A]
       |object Eq { def derived[A]: Eq[A] = ??? }
       |
       |enum Tree[T](x: Int) derives Eq
       |object A {
       |  implicit val eqInt: Eq[Int] = ???
       |  ${START}implicitly[Eq[Tree[Int]]]$END
       |}
       |""".stripMargin
  )

  def testMultipleTypeParameters(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait Eq[A]
       |object Eq { def derived[A]: Eq[A] = ??? }
       |
       |case class Foo[A, B, C](a: A, b: B, c: C) derives Eq
       |object A {
       |  given eqInt: Eq[Int] = ???
       |  given eqString: Eq[String] = ???
       |  given eqDouble: Eq[Double] = ???
       |  ${START}implicitly[Eq[Foo[Int, String, Double]]]$END
       |}
       |
       |""".stripMargin
  )

  def testCanEqual(): Unit = checkNoImplicitParameterProblems(
    s"""
       |class Foo[A, B, C[_], D[_, _]](a: A, b: B, c: C[A], d: D[A, B]) derives scala.CanEqual
       |object Foo {
       |  given cq: CanEqual[Double, Int] = ???
       |  ${START}implicitly[CanEqual[Foo[Double, String, List, [X, Y] =>> Int], Foo[Int, String, Option, [X,  Y] =>> String]]]$END
       |}
       |""".stripMargin
  )

  def testDeriveForTypeConstructorTC(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait Functor[F[_]]
       |object Functor { def derived[F[_]]: Functor[F] = ??? }
       |
       |case class Foo[A](a: A) derives Functor
       |object A {
       |  ${START}implicitly[Functor[Foo]]$END
       |}
       |""".stripMargin
  )

  def testCurriedDeriveTooManyTypeParams(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait Functor[F[_]]
       |object Functor { def derived[F[_]]: Functor[F] = ??? }
       |
       |case class Foo[A, B, C](a: A) derives Functor
       |object A {
       |  ${START}implicitly[Functor[[X] =>> Foo[Int, String, X]]]$END
       |}
       |""".stripMargin
  )

  def testCurriedDeriveTooFewTypeParams(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait Functor[F[_, _]]
       |object Functor { def derived[F[_]]: Functor[F] = ??? }
       |
       |case class Foo[A](a: A) derives Functor
       |object A {
       |  ${START}implicitly[Functor[[X, Y] =>> Foo[Y]]]$END
       |}
       |""".stripMargin
  )

  def testDerivedWithImplicitParameters(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait Ord[A]
       |trait Eq[A]
       |object Ord {
       |  def derived[A](implicit ev: Eq[A]): Ord[A] = ???
       |}
       |
       |case class Foo() derives Ord
       |object Foo {
       |  given eqFoo: Eq[Foo] = ???
       |}
       |
       |object A {
       |  ${START}implicitly[Ord[Foo]]$END
       |}
       |
       |""".stripMargin
  )

  def testDerivedObject(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait Eq[+A]
       |object Eq {
       |  object derived extends Eq[Any]
       |}
       |
       |trait Bar derives Eq
       |object A {
       |  ${START}implicitly[Eq[Bar]]$END
       |}
       |""".stripMargin
  )

  def testDerivedVal(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait Eq[+A]
       |object Eq {
       |  val derived: Eq[Any] = ???
       |}
       |
       |trait Bar derives Eq
       |object A {
       |  ${START}implicitly[Eq[Bar]]$END
       |}
       |""".stripMargin
  )

  def testSCL21404(): Unit = checkNoImplicitParameterProblems(
    s"""
      |trait Eq[A]
      |
      |object Eq {
      |  def derived[A]: Eq[A] = ???
      |}
      |
      |enum Color derives Eq {
      |  case Green
      |  case Red(x: Int)
      |}
      |
      |def foo[A: Eq](x: A): Unit = ???
      |
      |${START}foo(Color.Red(1))$END
      |""".stripMargin
  )
}
