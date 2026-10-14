package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

/**
 * The arguments of an invocation of a method, which only has leading using clauses, are those of an apply method
 * inserted on its result type, which may depend on the implicit arguments, e.g. `A` in
 * `def foo[A](using Foo[A]): Bar[A]` or `f.T` in `def foo(using f: Foo): Bar[f.T]`.
 */
class ApplyAfterUsingClauseTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  private val prelude =
    """
      |trait Foo[A]
      |given Foo[Int] = ???
      |trait Bar[B] {
      |  def apply[C](f: B => C): C = ???
      |}
      |def foo[A](using Foo[A]): Bar[A] = ???
      |""".stripMargin

  def testHighlighting(): Unit = checkTextHasNoErrors(
    prelude +
      """
        |val r = foo(x => x + 1)
        |""".stripMargin
  )

  def testResultType(): Unit = doTest(
    prelude +
      s"""
         |val r = ${START}foo(x => x + 1)$END
         |//Int
         |""".stripMargin
  )

  def testParameterType(): Unit = doTest(
    prelude +
      s"""
         |val r = foo(x => ${START}x$END + 1)
         |//Int
         |""".stripMargin
  )

  def testExplicitApply(): Unit = doTest(
    prelude +
      s"""
         |val r = foo.apply(x => ${START}x$END + 1)
         |//Int
         |""".stripMargin
  )

  def testNonFunctionArgument(): Unit = doTest(
    s"""
       |trait Foo[A]
       |given Foo[Int] = ???
       |trait Bar[B] {
       |  def apply[C](b: B, c: C): C = ???
       |}
       |def foo[A](using Foo[A]): Bar[A] = ???
       |val r = ${START}foo(1, "")$END
       |//String
       |""".stripMargin
  )

  def testWithExpectedType(): Unit = checkTextHasNoErrors(
    prelude +
      """
        |val r: Int = foo(x => x + 1)
        |""".stripMargin
  )

  def testUnderscore(): Unit = doTest(
    prelude +
      s"""
         |val r = ${START}foo(_ + 1)$END
         |//Int
         |""".stripMargin
  )

  def testNonGenericUsing(): Unit = doTest(
    s"""
       |trait Foo[A]
       |given Foo[Int] = ???
       |trait Bar[B] {
       |  def apply[C](f: B => C): C = ???
       |}
       |def foo(using Foo[Int]): Bar[Int] = ???
       |val r = foo(x => ${START}x$END + 1)
       |//Int
       |""".stripMargin
  )

  def testDependentResultType(): Unit = doTest(
    s"""
       |trait Foo { type T }
       |object IntFoo extends Foo { type T = Int }
       |given IntFoo.type = IntFoo
       |trait Bar[B] {
       |  def apply[C](f: B => C): C = ???
       |}
       |def foo(using f: Foo): Bar[f.T] = ???
       |val r = ${START}foo(x => x + 1)$END
       |//Int
       |""".stripMargin
  )

  // the shape of `async[IO] { io.await }` in cats-effect-cps
  def testDependentContextFunctionParameter(): Unit = checkTextHasNoErrors(
    """
      |trait Context[F[_]]
      |trait Monad[F[_]] { type Ctx <: Context[F] }
      |trait IO[A]
      |class IOContext extends Context[IO]
      |object IOMonad extends Monad[IO] { type Ctx = IOContext }
      |given IOMonad.type = IOMonad
      |class Runner[F[_], C <: Context[F]] {
      |  def apply[T](body: C ?=> T): F[T] = ???
      |}
      |def run[F[_]](using m: Monad[F]): Runner[F, m.Ctx] = ???
      |extension [F[_], A](self: F[A])(using Context[F]) {
      |  def await: A = ???
      |}
      |val io: IO[Int] = ???
      |val r: IO[IOContext] = run[IO](summon[IOContext])
      |val s: IO[Int] = run[IO](io.await)
      |""".stripMargin
  )
}
