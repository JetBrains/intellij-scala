package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class LocalTypeInferenceExpectedTypeTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL14442(): Unit = checkTextHasNoErrors(
    """
      |import scala.concurrent.ExecutionContext.Implicits.global
      |import scala.concurrent.Future
      |
      |def abc(): Future[Option[Int]] = {
      |  Future { Some(1) }.recover { case e => None }
      |}
      |
      |def abc1(): Future[Option[Int]] = {
      |  (for { f <- Future { Some(1) } } yield f ).recover { case e => None }
      |}
    """.stripMargin
  )

  def testSCL14534(): Unit = checkTextHasNoErrors(
    """
      |import scala.concurrent.ExecutionContext.Implicits.global
      |import scala.concurrent.Future
      |import scala.util.control.NonFatal
      |
      |case class Foo(n: Int)
      |
      |/** Takes a string and returns a future int-parsing of it */
      |def Goo(s: String): Future[Foo] = Future {
      |  Foo(java.lang.Integer.parseInt(s))  // will throw an exception on non-int
      |}
      |
      |def bar(s: String): Future[Either[String, Option[Foo]]] = {
      |  Goo(s).map { foo: Foo =>
      |    Right(if (foo.n > 0) Some(foo) else None)
      |  }.recover {
      |    case NonFatal(e) => Left("Failed to parse %s: %s".format(s, e))
      |  }
      |}
    """.stripMargin
  )

  def testSCL14862(): Unit = checkTextHasNoErrors(
    """
      |case class SadType() {
      |   val one: Option[SadType] = None
      |   val two: Option[SadType] = None
      |}
      |
      |trait SomeType {
      |
      |  def fold[A](one: => A, two: => A): A = ??? // generally it does something but this is good enough here
      |}
      |
      |object HereIsTheProblem {
      |
      |  type CompoundType = SadType => Option[SadType]
      |
      |  def problematicMethod(some: SomeType): CompoundType = some.fold(_.one, _.two) // HERE!
      |
      |}
    """.stripMargin
  )

  def testSCL14891(): Unit = checkTextHasNoErrors(
    """
      |final class Stream[+F[_], +O] {
      |  def flatMap[F2[x] >: F[x], O2](f: O => Stream[F2, O2]): Stream[F2, O2] = ???
      |  def map[O2](f: O => O2): Stream[F, O2] = ???
      |}
      |
      |class Foo[F[_]] {
      |
      |  val doLol: Stream[F, Int] = ???
      |
      |  def doKek: Stream[F, Unit] =
      |    for {
      |      _ <- doLol
      |      _ <- doLol
      |    } yield ()
      |}
    """.stripMargin
  )

  def testSCL14927(): Unit = checkTextHasNoErrors(
    """
      |trait UncaughtExceptionReporter
      |
      |abstract class Callback[-E, -A] extends (Either[E, A] => Unit)
      |object Callback {
      |  def empty[E, A](implicit r: UncaughtExceptionReporter): Callback[E, A] = ???
      |}
      |
      |trait Scheduler extends UncaughtExceptionReporter
      |trait Task[+A] {
      |  def f(cb: Either[Throwable, A] => Unit): Unit = ???
      |
      |  def runAsyncAndForgetOpt(implicit s: Scheduler): Unit =
      |    f(Callback.empty)
      |}
    """.stripMargin
  )

  def testSCL17198(): Unit = checkTextHasNoErrors(
    """
      |class Foo { def foo[A](f: A): A = f }
      |val f1: Foo = ???
      |val f: Int => Int = f1 foo
      |""".stripMargin
  )

  def testSCL17945(): Unit = checkTextHasNoErrors(
    """
      |object Error {
      |  trait State[G] {
      |    class SomeContext
      |    def m[T](action: => G => T)(implicit ev: SomeContext): G => T = ???
      |    def moveGeneration(g: SomeContext => G => Unit): Unit = ???
      |  }
      |  class Test extends State[Int] {
      |    moveGeneration { implicit context =>
      |      m(_ + 1)
      |    }
      |  }
      |}""".stripMargin
  )
}
