package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettingsProfile

class PartialUnificationTypeInferenceTest extends TypeInferenceTestBase {
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
}
