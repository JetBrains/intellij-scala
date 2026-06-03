package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.TypecheckerTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ScalaZTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def additionalLibraries: Seq[LibraryLoader] =
    Seq(IvyManagedLoader("org.scalaz" %% "scalaz-core" % "7.1.0"))

  //SCL-6096
  def testSCL6096(): Unit =
    checkTextHasNoErrors(
      s"""object Foo {
         |  import scalaz.Lens.lensg
         |  import scalaz.State
         |  import scalaz.syntax.traverse.ToTraverseOps
         |  import scalaz.std.indexedSeq.indexedSeqInstance
         |  // this "unused import" is required! ^^^
         |
         |  case class X(y: Int)
         |
         |  val y = lensg[X,Int](x => y => x.copy(y=y), _.y)
         |
         |  def foo(x: X):String = x.toString
         |  def foo(x: Int): String = "ok"
         |  def sequenced(x: X, s: State[X,Any]*) =
         |    foo(s.toIndexedSeq.sequenceU.exec(x))
         |}
         |""".stripMargin
    )

  //SCL-9762
  def testSCL9762(): Unit =
    checkTextHasNoErrors(
      s"""
         |import scalaz._
         |import Scalaz._
         |import Kleisli._
         |
         |object BadKleisli {
         |  val k : Kleisli[Option, Int, String] = ${START}kleisliU ((i: Int) => i.toString.some )$END
         |}
      """.stripMargin
    )

  //SCL-9989
  def testSCL9989(): Unit =
    checkTextHasNoErrors(
      s"""
         |import scala.concurrent.ExecutionContext.Implicits.global
         |import scala.concurrent.Future
         |import scalaz.Scalaz._
         |
         |class Comparison {
         |
         |  def function1(inputParam: String): Future[Option[String]] = ???
         |
         |  def function2(inputParam: String): Future[Option[String]] = ???
         |
         |  def allTogetherWithTraverseM: Future[Option[String]] = {
         |    for {
         |      res1 <- function1("input")
         |      res2 <- res1.traverseM(param => function2(${START}param$END))
         |    } yield res2
         |  }
         |}
         |""".stripMargin
    )

  //SCL-5706
  def testSCL5706(): Unit =
    checkTextHasNoErrors(
      s"""
         |import scalaz._, Scalaz._
         |object Application {
         |  type Va[+A] = ValidationNel[String, A]
         |
         |  def v[A](field: String, validations: Va[A]*): (String, Va[List[A]]) = {
         |    (field, ${START}validations.toList.sequence$END)
         |  }
         |}
         |
       """.stripMargin
    )
}
