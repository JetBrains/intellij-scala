package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * @author mucianm
  * @since 28.03.16.
  */
class ScalaZTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override protected def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+ IvyManagedLoader("org.scalaz" %% "scalaz-core" % "7.1.0")

  def testSCL5706(): Unit = {
    doTest(
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
         |
         |//Va[List[A]]
       """.stripMargin
    )
  }

  def testSCL6096(): Unit = {
    doTest(
      s"""
         |import scalaz.Lens.lensg
         |import scalaz.State
         |import scalaz.syntax.traverse.ToTraverseOps
         |import scalaz.std.indexedSeq.indexedSeqInstance
         |// this "unused import" is required! ^^^
         |
         |case class X(y: Int)
         |
         |def foo(x: X):String = x.toString
         |def foo(x: Int): String = "ok"
         |def sequenced(x: X, s: State[X,Any]*) =
         |  foo(${START}s.toIndexedSeq.sequenceU.exec(x)$END)
         |//Int
      """.stripMargin)
  }

  def testSCL9762(): Unit = {
    doTest(
      s"""
         |import scalaz._
         |import Scalaz._
         |import Kleisli._
         |
         |object BadKleisli {
         |  val k : Kleisli[Option, Int, String] = ${START}kleisliU ((i: Int) => i.toString.some )$END
         |}
         |//Kleisli[Option, Int, String]
      """.stripMargin)
  }

  def testSCL9989(): Unit = {
    doTest(
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
         |//_G[Option[_B]]
      """.stripMargin)
  }
}
