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
}
